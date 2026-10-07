# Data Model: 로그인·로그아웃 (001-auth)

**Phase 1 산출물** · 작성일 2026-10-07

이 기능은 **새 테이블·컬럼·마이그레이션을 만들지 않는다.** PostgreSQL 엔터티는 [docs/51-erd-unified.md](../../docs/51-erd-unified.md)(V1 기준선)의 정의를 그대로 쓰고, 토큰·카운터·세션은 Redis에 둔다(07 §8). 제약 이름·타입은 51과 같다. 결정 근거는 [research.md](./research.md).

---

## 1. PostgreSQL 엔터티 (공통 ERD, 수정 없음)

### 1.1 member — 회원 (51 §2, 이 기능이 쓰는 컬럼)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | bigint IDENTITY, PK | 세션 principal 이름(`memberId` 문자열) |
| handle | varchar(39) NOT NULL, `uq_member_handle`, `ck_member_handle` | 가입 시 생성(이메일 가입은 접두어 없음, Google `go-`, GitHub `gi-`). 규칙은 002(08 문서) |
| nickname | varchar(10), `uq_member_nickname`(lower), `ck_member_nickname` | 가입 시 필수. 소셜은 소셜 이름을 정리해 미리 채움(09) |
| role | varchar(20) DEFAULT 'USER' | 세션 권한(`ROLE_USER`/`ROLE_ADMIN`) |
| status | varchar(20) DEFAULT 'ACTIVE', `ck_member_status` | 로그인 시 판정: `WITHDRAWN` → 복구 전용 세션, `SUSPENDED` → R-9 |
| withdrawn_at, deleted_at | timestamptz | `deleted_at IS NOT NULL`(익명 처리)은 로그인 수단이 지워져 로그인 대상이 아님 |
| created_at, updated_at | timestamptz | 가입 시각 = 소셜 가입의 `email_verified_at` 값과 같은 시각 사용 |

다른 컬럼(`bio`, `profile_image_id`, `profile_image_url`, `default_visibility`, `nickname_changed_at`)은 기본값으로 두고 이 기능에서 바꾸지 않는다.

### 1.2 auth_identity — 로그인 수단 (51 §2, 9개 컬럼 전부)

| 컬럼 | 타입 | 규칙 |
|---|---|---|
| id | bigint IDENTITY, PK | |
| member_id | bigint NOT NULL, FK → member RESTRICT, `uq_auth_identity_member` | 계정 하나 = 수단 하나 (FR-003) |
| provider | varchar(20) NOT NULL, `ck_auth_provider` | `LOCAL` / `GOOGLE` / `GITHUB` |
| provider_user_id | varchar(255) NOT NULL | `LOCAL` = 소문자·trim 이메일, `GOOGLE` = `sub`, `GITHUB` = 숫자 ID 문자열 (FR-004) |
| email | varchar(255) NULL, `ix_auth_identity_email` | `LOCAL` 필수·소문자. 소셜은 인증된 이메일 또는 GitHub 마무리 화면 입력값(소문자) |
| password_hash | varchar(100) NULL, `ck_auth_password` | `LOCAL`만 NOT NULL. `{bcrypt}` 접두 해시 |
| email_verified_at | timestamptz NULL | NULL = 인증 전 → 쓰기 403 `EMAIL_NOT_VERIFIED`. 소셜(인증된 이메일)은 가입 시각 |
| created_at | timestamptz DEFAULT now | |
| last_login_at | timestamptz NULL | 로그인 성공 시 갱신 (FR-029) |

**DB가 보장하는 것**: `uq_auth_identity (provider, provider_user_id)` → 같은 수단 중복 계정 금지(FR-002, SC-001). `ck_auth_local_email` → `LOCAL`은 `email = lower(email) AND provider_user_id = email`.

**Service가 보장하는 것**: 이메일 정규화(trim + 소문자, 254자 이하, 형식), 소셜 접두어와 수단 일치(08 §2), GitHub 로그인 이름을 식별에 쓰지 않음.

### 1.3 member_agreement — 회원 동의 (51 §2)

| 컬럼 | 타입 | 규칙 |
|---|---|---|
| member_id | bigint, PK1, FK → member RESTRICT | |
| type | varchar(20), PK2, `ck_member_agreement_type` | 이 기능은 `TERMS`, `PRIVACY` 두 행을 만든다 (`AI`는 34 기능) |
| agreed_at | timestamptz DEFAULT now | 동의 시각 (FR-006) |

- 가입 트랜잭션에서 두 행을 함께 만든다. 둘 중 하나라도 동의하지 않은 요청은 계정 생성 전에 거부한다. DB에는 "필수 동의" 제약이 없으므로 이 규칙은 Service 책임이다(51 §4).
- 07 §3·§8과 source-notes의 `member.terms_agreed_at`·`privacy_agreed_at`는 51에서 이 테이블로 옮겨졌다(research R-14).

### 1.4 member_suspension — 회원 정지 이력 (51 §2, 읽기 + 자동 해제)

| 컬럼 | 이 기능에서의 쓰임 |
|---|---|
| member_id, reason, ends_at | 로그인 거부 안내 "정지된 계정이에요 (~ends_at, reason)". `ends_at` NULL = 영구 |
| lifted_at, lifted_by | 기간이 끝난 정지를 로그인 시 자동 해제: `lifted_at = now()`, `lifted_by = NULL` |

- 현재 정지 판정: `lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now())` 행 존재 (`ix_member_suspension_member` 사용).
- 정지 등록·관리자 해제는 43 기능 범위.

---

## 2. Redis 엔터티 (TTL 기반, 테이블 없음)

키 이름·TTL·한도 전체는 [contracts/redis-keys.md](./contracts/redis-keys.md). 여기서는 의미만 적는다.

| 엔터티 | 키 요약 | 값 | 수명 |
|---|---|---|---|
| 이메일 인증 토큰 | `auth:verify:{sha256(token)}` + 회원별 포인터 | memberId | 24시간, 사용 시 삭제, 재발송 시 이전 것 삭제 |
| 비밀번호 재설정 토큰 | `auth:reset:{sha256(token)}` + 회원별 포인터 | memberId | 30분, 사용 시 삭제, 재요청 시 이전 것 삭제 |
| 재발송·재설정 요청 카운터 | `auth:verify-resend:*`, `auth:reset-req:*` | 정수 | 1분 / 1일 / 1시간 창 |
| 로그인 실패 카운터·잠금 | `auth:login-fail:{emailHash}`, `auth:login-lock:{emailHash}`, `auth:login-ip:{ip}` | 정수 / 표시 | 15분 / 1분 창 |
| 로그인 세션 | Spring Session 인덱스 저장소 키 | 세션 속성(SecurityContext, CSRF 토큰 등) | 마지막 활동 후 14일 |
| 소셜 가입 대기 정보 | 세션 속성 `PENDING_SOCIAL_SIGNUP` | provider, providerUserId, verifiedEmail?, displayName, pictureUrl, createdAt | 10분 (createdAt 기준, 지나면 무효) |

`PendingSocialSignup`의 사진 주소는 화면에 넘기는 데만 쓰고 DB에 저장하지 않는다(11 §4-2, 소셜 주소 비저장).

---

## 3. 검증 규칙 (요구사항 매핑)

| 대상 | 규칙 | 근거 |
|---|---|---|
| 이메일 | trim + 소문자, 일반 이메일 형식, 최대 254자 | FR-004, FR-005 |
| 비밀번호 | 8~16자, 영문·숫자·특수문자 각 1+, 허용 문자만(07 §4 목록), 이메일 앞부분(3자 이상) 포함 금지, 흔한 비밀번호 금지, 비밀번호 확인 일치 | FR-012, FR-013 |
| 약관 | `TERMS`·`PRIVACY` 모두 동의 | FR-006 |
| 블로그 주소 | 08 규칙 + 수단별 접두어 일치, 가입 시 한 번 수정 | FR-005 (002) |
| 닉네임 | 09 규칙(2~10자, NFC, 금칙어·예약어, 대소문자 무시 유일) | FR-005, FR-021 (002) |
| 로그인 후 이동 | `/`로 시작하는 상대 경로, `//`·`/\` 금지 | FR-027 |
| 소셜 이메일 | Google `email_verified = true`만, GitHub `primary && verified`만 | FR-022 |

---

## 4. 상태 전이

### 4.1 이메일 인증 (auth_identity.email_verified_at)

```text
[미인증: email_verified_at = NULL]
   ├─ 유효한 인증 링크 사용 ─────────▶ [인증됨: email_verified_at = 사용 시각]
   ├─ 재발송(1분 1회·하루 10회) ─────▶ [미인증] (새 토큰, 이전 토큰 삭제)
   └─ 만료·사용된 링크 ──────────────▶ [미인증] ("링크가 만료됐어요")
소셜 가입(인증된 이메일) ────────────▶ [인증됨: 가입 시각]
GitHub 가입(인증된 이메일 없음, 입력) ▶ [미인증] → 위와 같은 흐름
```

인증됨 → 미인증으로 돌아가는 전이는 없다(이메일 변경 불가, 결정 기록 "프로필 저장").

### 4.2 로그인 시도 판정

```text
IP 한도 초과? ── 예 ─▶ 거부(잠시 후 다시)
   │아니오
계정 잠금 중? ── 예 ─▶ "잠시 후 다시 시도해 주세요(약 15분)"  (없는 이메일도 동일)
   │아니오
자격 증명 일치? ─ 아니오 ─▶ 실패 카운터 +1 (5회째 잠금) → "이메일 또는 비밀번호가 올바르지 않아요"
   │예
현재 정지 있음? ─ 예 ─▶ 로그인 거부 + ACCOUNT_SUSPENDED (기한·사유)
   │아니오 (기간 지난 정지는 자동 해제)
탈퇴 유예? ───── 예 ─▶ 복구 전용 세션 → 복구 화면만
   │아니오
로그인 성공: 세션 ID 새로 발급, 실패 카운터 삭제, last_login_at 갱신, 이동(상대 경로)
```

### 4.3 소셜 첫 로그인

```text
OAuth 콜백(state 검증) → (provider, id) 계정 있음? ─ 예 ─▶ 4.2의 "현재 정지 있음?"부터
                                          │아니오
                         PendingSocialSignup(10분) → 가입 마무리 화면
                           (+ 같은 이메일 다른 수단 계정 있으면 FR-033 안내)
                           ├─ [기존 계정으로 로그인] → 대기 정보 삭제 → 로그인 화면
                           ├─ 10분 경과 → 대기 정보 무효 → 소셜 인증부터 다시
                           └─ 완료 → member + auth_identity + member_agreement×2 (한 트랜잭션)
                                     ├─ 제약 위반(동시 완료) → 이미 생긴 계정으로 로그인
                                     └─ 성공 → 로그인 (세션 ID 새로 발급)
```

### 4.4 세션 수명

```text
로그인 → [활성] ─ 요청마다 14일 연장 ─▶ [활성]
   ├─ 14일 무활동 ─▶ [만료]
   ├─ 로그아웃 ─▶ [삭제] (+ 브라우저 임시 데이터 삭제)
   ├─ 비밀번호 재설정 ─▶ 그 회원의 모든 세션 [삭제] (FR-019)
   └─ 정지(43) ─▶ 그 회원의 모든 세션 [삭제] (42 P-7, 이 기능이 제공하는 Service 사용)
```

---

## 5. 관계 요약

```text
member 1 ──── 1 auth_identity            (uq_auth_identity_member)
member 1 ──── 0..3 member_agreement      (이 기능은 TERMS, PRIVACY 2행)
member 1 ──── 0..* member_suspension     (읽기·자동 해제만)
member 1 ──── 0..* 로그인 세션 (Redis, principal = memberId)
member 1 ──── 0..1 인증 토큰 / 0..1 재설정 토큰 (Redis)
```
