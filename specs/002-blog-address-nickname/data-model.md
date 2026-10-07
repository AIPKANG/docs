# Data Model: 블로그 주소(아이디)와 닉네임 (002-blog-address-nickname)

**Phase 1 산출물** · 작성일 2026-10-07

이 기능은 **새 테이블·컬럼·인덱스·마이그레이션을 만들지 않는다.** PostgreSQL 엔터티는 [docs/51-erd-unified.md](../../docs/51-erd-unified.md)(V1 기준선)의 `member` 정의를 그대로 쓰고, 예약어·금칙어 목록은 설정 파일, 요청 제한 카운터는 Redis에 둔다. 제약 이름·타입은 51과 같다. 결정 근거는 [research.md](./research.md). 001-auth의 [data-model.md](../001-auth/data-model.md)와 같은 `member` 행을 다루며, 이 문서는 그중 이 기능이 소유하는 컬럼 규칙을 정한다.

---

## 1. PostgreSQL 엔터티 (공통 ERD, 수정 없음)

### 1.1 member — 회원 (51 §2, 이 기능이 소유하는 컬럼)

| 컬럼 | 타입 | 제약 (51 §3) | 규칙 |
|---|---|---|---|
| handle | varchar(39) NOT NULL | `uq_member_handle` UNIQUE(handle), `ck_member_handle` CHECK(handle ~ `'^((go\|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$'`) | `[접두어-]본문`. 접두어는 없음(LOCAL)·`go-`(GOOGLE)·`gi-`(GITHUB). 본문 3~36자 소문자·숫자·`_`, 처음·끝은 영숫자. **가입 때 한 번 정하고 이후 어떤 UPDATE도 하지 않는다.** 탈퇴·익명 처리 후에도 값이 남아 재사용 불가 (FR-001~FR-013) |
| nickname | varchar(10) NULL | `uq_member_nickname` UNIQUE INDEX ON (lower(nickname)), `ck_member_nickname` CHECK(nickname ~ `'^[가-힣a-zA-Z0-9]{2,10}$'` AND nickname ~ `'[가-힣a-zA-Z]'`), `ck_member_nickname_null` CHECK(nickname IS NOT NULL OR deleted_at IS NOT NULL) | 정리(trim + NFC)된 값을 입력한 대소문자 그대로 저장. 대소문자 무시 유일. NULL은 익명 처리 후만 (FR-014~FR-026) |
| nickname_changed_at | timestamptz NULL | — | 가입 시 NULL(가입 닉네임은 변경으로 세지 않음). 실제 변경 시 `now()`. 30일 제한 기준 (FR-023, FR-024) |

이 기능이 **읽기만** 하는 컬럼

| 컬럼 | 쓰임 |
|---|---|
| id | 닉네임 변경 대상(현재 로그인 회원), 중복 검사에서 자기 자신 제외 |
| status, withdrawn_at, deleted_at | `/@{handle}` 주인 찾기: `status = 'WITHDRAWN'`이면 없음(404). 표시: `withdrawn_at IS NOT NULL`이면 "탈퇴한 사용자" |
| bio, profile_image_url | `BlogOwner` 요약에 담아 블로그 상단(009)에 넘김. 값 변경은 003 |
| updated_at | 닉네임 변경 시 함께 갱신 |

이 기능이 **쓰는 시점**

| 시점 | 쓰는 컬럼 | 호출자 |
|---|---|---|
| 이메일·소셜 가입 (한 트랜잭션) | `handle`, `nickname`, `nickname_changed_at = NULL` | 001 `EmailSignupService`·`SocialSignupService`가 `HandleService.validateForSignup`, `NicknamePolicy.validate` 결과로 `Member`를 만든다 |
| 닉네임 변경 | `nickname`, `nickname_changed_at`, `updated_at` | 003 `ProfileService` → `NicknameChangeService.change` |
| 익명 처리 | `nickname = NULL`, `nickname_changed_at = NULL` (handle은 그대로) | 탈퇴 기능(13·44, order 90). 이 기능은 규칙만 정한다 |

### 1.2 관련 테이블 (읽기만)

| 테이블 | 쓰임 |
|---|---|
| auth_identity | 소셜 동시 가입 번역(research R-7): `(provider, provider_user_id)` 계정이 이미 생겼는지 확인. 접두어 일치 검사는 가입 Command의 provider 값으로 하고 이 테이블을 조회하지 않는다 |

---

## 2. 도메인 값 (코드, 테이블 없음)

| 값 | 필드 | 규칙 |
|---|---|---|
| `Handle` | `prefix`(NONE/GO/GI), `body` | 생성 시 형식 검사. `toString()` = `prefix + body`. `Provider`(001: LOCAL/GOOGLE/GITHUB) ↔ 접두어 1:1 대응 |
| `HandlePrefix` | `NONE("")`, `GO("go-")`, `GI("gi-")` | `of(Provider)`. 새 소셜 수단은 값 추가 + DB CHECK 변경(08 §7, 이번 범위 아님) |
| `Nickname` | `value`(NFC) | 생성 시 정리·형식·글자 포함 검사. 비교용 `lowerKey()` |
| `HandleCheckResult` | `available`, `reason`(HandleViolation?), `suggestion`(String?) | 사용 가능 여부 API 응답의 원천 |
| `NicknameCheckResult` | `normalized`, `violation`(NicknameViolation?) | 가입·변경·API 공용 |
| `NicknameChangeResult` | `CHANGED(changedAt)` / `UNCHANGED` | 같은 값 재저장은 `UNCHANGED` |
| `AuthorDisplay` | `handle`, `nickname`(nullable), `withdrawn` | 표시 규칙(research R-16) |
| `BlogOwner` | `memberId`, `handle`, `nickname`, `bio`, `profileImageUrl` | `/@{handle}` 주인 요약(활성 회원만) |

### 2.1 오류 코드

| 코드 | 대상 | HTTP(REST) / SSR | 화면 문구 | 대안 제안 |
|---|---|---|---|---|
| `HANDLE_INVALID_FORMAT` | 주소 | 400 / 필드 안내 | "영문 소문자·숫자·_로 3~36자까지 쓸 수 있어요" | 없음 |
| `HANDLE_PREFIX_MISMATCH` | 주소 | 400 / 필드 안내 | "가입 방법과 맞지 않는 주소예요" | 없음 |
| `HANDLE_RESERVED` | 주소 | 400 / 필드 안내 | "사용할 수 없는 주소예요" | `base_n` |
| `HANDLE_BANNED_WORD` | 주소 | 400 / 필드 안내 | "사용할 수 없는 단어가 들어 있어요" | 없음 |
| `HANDLE_DUPLICATE` | 주소 | 400 / 필드 안내 | "이미 사용 중인 주소예요" | `base_n` |
| `HANDLE_TAKEN_CONCURRENTLY` | 주소(가입 제출 경합) | 409 / 필드 안내 | "방금 다른 분이 이 주소를 사용했어요. `{suggestion}`는 어떠세요?" | `base_n` |
| `NICKNAME_INVALID_FORMAT` | 닉네임 | 400 | "한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)" | — |
| `NICKNAME_LETTER_REQUIRED` | 닉네임 | 400 | "한글이나 영문을 1자 이상 넣어 주세요" | — |
| `NICKNAME_RESERVED` | 닉네임 | 400 | "사용할 수 없는 닉네임이에요" | — |
| `NICKNAME_BANNED_WORD` | 닉네임 | 400 | "사용할 수 없는 단어가 들어 있어요" (단어 비노출) | — |
| `NICKNAME_DUPLICATE` | 닉네임 | 400 (경합으로 진 쪽은 409) | "이미 사용 중인 닉네임이에요" / 경합: "방금 다른 분이 이 닉네임을 사용했어요" | — |
| `NICKNAME_CHANGE_TOO_SOON` | 닉네임 변경 | **409** (42 §4) | "다음 변경 가능일: {M월 d일}" | — |
| `RATE_LIMITED` | 사용 가능 여부 API | 429 + `Retry-After` | "잠시 후 다시 확인해 주세요" | — |

- 400은 42 §4 "요청 자체가 규칙에 어긋남", 409는 "상태 충돌"(경합에서 진 쪽, 30일 제한)에 맞춘다. 429는 research U-2(팀 확인).
- 주소 문구 중 `HANDLE_INVALID_FORMAT`·`HANDLE_PREFIX_MISMATCH`·`HANDLE_RESERVED`·`HANDLE_DUPLICATE`는 08에 문구가 없어 이 계획이 정했다(09 문구와 같은 말투).

---

## 3. 설정 (정책 수치·목록, 헌법 II)

| 키 | 기본값 | 근거 |
|---|---|---|
| `blog.account.handle.reserved` | 08 §5 목록 34개 (admin … teamblog) | FR-004 |
| `blog.account.handle.prefill-max-body-length` | 30 | 08 §3 7단계 |
| `blog.account.handle.suggestion-batch-size` | 20 | research R-3 |
| `blog.account.nickname.reserved` | 09 §5 목록 15개 (관리자 … root) | FR-017 |
| `blog.account.nickname.change-cooldown` | 30d | FR-023 |
| `blog.account.availability.per-ip-per-minute` | 30 | FR-009, FR-021 |
| `blog.text.banned-words.location` | `classpath:policy/banned-words.txt` | FR-016 |
| `blog.text.banned-words.exceptions-location` | `classpath:policy/banned-words-exceptions.txt` | FR-016 |

형식 정규식(주소·닉네임)은 DB CHECK와 같아야 하므로 설정이 아니라 코드 상수다(바꾸려면 Flyway 마이그레이션이 함께 필요).

---

## 4. Redis 엔터티 (요청 제한만)

| 키 | 값 | TTL | 한도 |
|---|---|---|---|
| `account:handle-check:ip:{ip}` | 횟수 | 1분 | 30회 — `GET /api/handles/availability`, `POST /api/handles/suggestion` 공용 |
| `account:nickname-check:ip:{ip}` | 횟수 | 1분 | 30회 — `GET /api/nicknames/availability` |

증가·만료는 001의 `RedisRateLimiter`(Lua 원자 처리)를 쓴다([001 redis-keys.md](../001-auth/contracts/redis-keys.md) 규칙과 같음). 다른 모듈은 이 키를 직접 쓰지 않는다.

---

## 5. 검증 규칙 (요구사항 매핑)

| 대상 | 규칙 | 근거 |
|---|---|---|
| 주소 미리 채우기 | 08 §3 1~10단계 (마지막 `@` 기준, `+` 뒤 버림, 소문자, `.`·`-`→`_`, 허용 외 제거, `_` 정리, 30자, 3자 미만이면 `user_`+6자리, 접두어, `_n`) | FR-006, SC-001 |
| 주소 제출 | 정리(trim·소문자) → 접두어 해석·일치 → 형식 → 예약어(본문 정확 일치) → 금칙어(본문, `_` 제거 변형 포함) → 중복 | FR-001~FR-005, FR-010 |
| 주소 변경 | 가입 후 어떤 API·Service도 `handle`을 바꾸지 않는다. `Member.handle`은 생성자에서만 설정(변경 메서드 없음, JPA `updatable = false`) | FR-011, SC-004 |
| 닉네임 | 정리(trim·NFC) → 형식 → 글자 포함 → 예약어(4변형 포함 검사) → 금칙어(4변형, 예외 먼저 제거) → 중복(lower, 자기 제외) | FR-014~FR-020 |
| 소셜 닉네임 미리 채우기 | NFC → 허용 외 제거 → 10자 → 전체 검사 통과 시만 채움 | FR-022 |
| 닉네임 변경 | 본인만, 같은 값이면 무시, 30일 제한, 대소문자만 변경도 변경 | FR-023~FR-025 |

---

## 6. 상태 전이

### 6.1 블로그 주소 (handle)

```text
[가입 화면: 미리 채움(서버 제안)] ─ 사용자가 고침(1회 이상, 가입 전까지 자유) ─▶ [제출값]
[제출값] ─ 서버 검사 통과 + INSERT 성공 ─▶ [확정: 불변]
        ├─ 규칙 위반 ─▶ 400 + 이유(+ 대안) → 다시 입력
        └─ uq_member_handle 경합 ─▶ 409 "방금 다른 분이…" + 대안 → 다시 입력
[확정] ─ 탈퇴 유예 ─▶ [확정: 블로그 404] ─ 복구 ─▶ [확정]
                                         └─ 30일 뒤 익명 처리 ─▶ [영구 보존: 404, 재사용 불가]
```

### 6.2 닉네임 (nickname, nickname_changed_at)

```text
가입 ─▶ [설정됨, changed_at = NULL] ─ 변경 ─▶ [설정됨, changed_at = t]
[설정됨, changed_at = t]
   ├─ t + 30일 전 변경 시도 ─▶ 409 NICKNAME_CHANGE_TOO_SOON (값 그대로)
   ├─ 같은 값 재저장 ─▶ UNCHANGED (changed_at 그대로)
   └─ t + 30일 이후 변경 ─▶ [설정됨, changed_at = t'] (이전 값 즉시 해제)
어느 상태든 ─ 탈퇴 유예 ─▶ [묶임: 값 유지, 다른 사람 사용 불가]
                         ├─ 복구 ─▶ 이전 상태
                         └─ 익명 처리 ─▶ [NULL: 해제] (ck_member_nickname_null 충족)
```

---

## 7. 관계 요약

```text
member 1 ──── 1 handle (불변, 영구)
member 1 ──── 0..1 nickname (익명 처리 후 NULL)
member 1 ──── 1 auth_identity (001) ── provider ↔ handle 접두어 (가입 Service에서 일치 검사)
설정 목록 (주소 예약어 / 닉네임 예약어 / 금칙어 / 예외) ── 검사에서 읽기만
```
