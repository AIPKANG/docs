# Research: 블로그 주소(아이디)와 닉네임 (002-blog-address-nickname)

**Phase 0 산출물** · 작성일 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `.specify/memory/constitution.md`, `docs/08-blog-address.md`, `docs/09-nickname.md`, `docs/01-common-requirements.md` §4(결정 기록, 우선), `docs/42-permission-matrix.md`, `docs/51-erd-unified.md`, `docs/52-erd-review.md`, 참고 `docs/11-profile.md` §5, `docs/13-delete-withdraw.md` D-8~D-10, `docs/40-post-detail.md`, `docs/21-comment.md`, [001-auth 계획](../001-auth/plan.md)(특히 [research R-17](../001-auth/research.md))

각 항목은 **Decision / Rationale / Alternatives considered** 형식이다. 001-auth에서 이미 정한 기술 결정(R-1 SSR, R-2 Boot 4.1.1, R-6 Redis 요청 제한, R-8 DB 제약 최종 보장, R-15 테스트, R-16 Gradle)은 그대로 이어받고 다시 적지 않는다.

---

## R-1. 기술 기준 (001과 동일, 사용자 확정 2026-10-07)

- **Decision**: Java 21, Spring Boot 4.1.1, Gradle Wrapper 9.8.0(Kotlin DSL), Thymeleaf SSR + Spring Session Data Redis, PostgreSQL 18 + Flyway, Testcontainers. 패키지는 `com.team.blog`, 기능은 `account` 모듈 안에 둔다.
- **Rationale**: 사용자 확정값이며 001 plan과 같은 빌드·실행 단위를 공유한다(헌법 I). 블로그 주소·닉네임은 `member` 테이블의 컬럼이므로 `member`를 소유한 `account` 모듈에 속한다.
- **Alternatives considered**: 별도 `profile`/`identity` 모듈 — `member` 테이블을 두 모듈이 나눠 쓰게 되어 헌법 I(다른 모듈 테이블 직접 사용 금지)에 어긋난다.

## R-2. 규칙의 소유와 001·003에 내보내는 Service (001 R-17 이행)

- **Decision**: 002가 다음 공개 Service의 명세·구현·테스트를 소유한다. 시그니처는 [contracts/account-identity-service.md](./contracts/account-identity-service.md).
  - `account.application.HandleService` — 이메일로 주소 만들기(08 §3 10단계), 가입 시 주소 검사(형식·접두어·예약어·금칙어·중복), 사용 가능 여부, 대안 주소 제안, 접속 주소 정규화.
  - `account.application.NicknamePolicy` — 정리(trim+NFC) → 형식 → 글자 포함 → 예약어 → 금칙어 → 중복 검사 한곳(09 §3). 소셜 이름 미리 채우기.
  - `account.application.NicknameChangeService` — 30일 제한·같은 값 무시·대소문자만 변경 처리. 003 `ProfileService`가 자기 트랜잭션 안에서 호출한다.
  - `account.application.BlogOwnerResolver` — `/@{handle}` 주인 찾기(활성 회원만, 그 외 없음 → 404).
  - `account.application.MemberUniqueViolationTranslator` — `uq_member_handle`·`uq_member_nickname` 위반을 사용자 안내 예외로 바꾼다(동시 가입·변경의 진 쪽).
  - `shared.text.BannedWordFilter` — 금칙어 필터(4가지 변형 + 예외 목록). 닉네임·블로그 주소·소개(003)가 함께 쓴다.
  - 표시 규칙: `account.application.AuthorDisplay` 값 객체와 Thymeleaf 조각 `fragments/author`.
- **Rationale**: 001 R-17이 "가입은 `HandleService`·`NicknamePolicy`를 호출하고, 두 규칙의 명세·테스트 소유는 002"로 정했다. 09 §3 "모든 검사는 `NicknamePolicy` 한곳", spec FR-020(가입·소셜 가입·프로필 수정이 같은 정책). 이름은 001 plan의 Project Structure에 이미 적힌 이름을 그대로 쓴다.
- **Alternatives considered**: 001·003이 각자 검사 — 규칙이 세 곳으로 갈라진다. `NicknamePolicy`를 순수 도메인 객체로만 두고 중복 검사를 호출자에 맡김 — 검사 순서(⑥ 중복이 마지막)를 호출자마다 다시 지켜야 해서 09 §3과 어긋날 위험이 크다. 대신 순수 규칙 부분은 `account.domain.NicknameRules`로 분리해 단위 테스트한다.

## R-3. 블로그 주소 만들기 (FR-006, SC-001)

- **Decision**: `account.domain.HandleRules`(순수 함수)가 08 §3의 1~9단계를 그대로 구현하고, 10단계(예약어·이미 있음 → `_2`, `_3`…)는 `HandleService`가 DB 조회로 처리한다.
  - 1단계 `@` 앞부분은 **마지막 `@`** 기준으로 자른다(로컬 부분에 따옴표로 `@`가 들어간 예외적 이메일 대비).
  - 8단계 난수: `SecureRandom`으로 `000000`~`999999` 6자리(앞자리 0 허용, 항상 6자리). 테스트에서는 난수원을 주입해 `user_483920` 예시를 재현한다.
  - 10단계 번호 찾기: 후보 `base`, `base_2` … `base_21`을 **`handle IN (...)` 한 번**으로 조회해(`uq_member_handle` 인덱스 사용) 비어 있는 첫 값을 고르고, 모두 차 있으면 다음 20개를 조회한다. `base`가 예약어이면 `base` 자체는 후보에서 뺀다.
  - 길이: 7단계에서 본문이 30자 이하이므로 `_` + 5자리 번호까지 붙여도 본문 36자 안이다. 사용자가 직접 고친 긴 본문(최대 36자)에 번호를 붙일 때는 **본문 끝을 잘라** 36자를 넘지 않게 하고, 잘린 끝의 `_`는 지운다.
- **Rationale**: 08 §3 예시표가 실행 검증된 결과이고 SC-001은 그 12개를 그대로 재현해야 한다. 순수 함수로 두면 예시표를 매개변수 테스트로 그대로 옮길 수 있다. `LIKE 'base\_%'` 조회는 기본 collation에서 B-tree를 쓰지 못할 수 있어 `IN` 묶음 조회를 택했다.
- **Alternatives considered**: `SELECT max(번호)` + 1 — "비어 있는 첫 번호"(08 §3 10단계)와 다르다(중간 번호가 비어도 건너뛴다). 무작위 접미사 — 08 H-5 결정(`_2`, `_3`)과 다르다.

## R-4. 가입 화면의 주소 미리 채우기 경로 (FR-007)

- **Decision**: **서버 한곳에서 계산한다.** 이메일 가입 화면은 이메일 입력이 0.5초 멈추면 `POST /api/handles/suggestion`(본문 `{ "email": … }`, CSRF 토큰 포함)을 호출해 받은 값을 주소 칸에 넣는다. 사용자가 주소 칸을 한 번이라도 직접 고치면(입력 이벤트 기준, 화면 상태 `handleTouched`) 그 뒤로는 호출하지 않는다. 소셜 가입 마무리 화면은 서버가 화면을 그릴 때 `HandleService.prefill(email, provider)` 결과를 넣는다(인증된 이메일이 없으면 `user_` 규칙). 요청 제한은 주소 확인과 같은 IP 버킷(1분 30회)을 쓴다.
- **Rationale**: 08 §3 알고리즘을 JS로 한 벌 더 만들면 두 구현이 어긋날 수 있고, SC-001(12개 예시 동일)을 서버 테스트 하나로 보장할 수 없다. 이메일은 개인정보라 URL 쿼리(접근 로그에 남음) 대신 POST 본문으로 보낸다. 응답은 공개 정보인 블로그 주소 후보뿐이고 그 이메일의 가입 여부를 드러내지 않는다(같은 앞부분의 주소가 있는지만 알 수 있고, 주소는 원래 공개 정보다 — 08 §4-2).
- **Alternatives considered**: (a) JS에 같은 알고리즘 구현 — 즉시 반응하지만 규칙이 두 곳. (b) `GET /api/handles/availability`에 `email` 매개변수 추가 — 이메일이 URL에 남는다. (c) 미리 채우기 없이 빈칸 — 08 H-4 결정과 다르다.

## R-5. 가입 요청의 주소 검사와 접두어 일치 (FR-003, FR-010)

- **Decision**: 001의 `POST /signup`·`POST /signup/social`이 보내는 `handle` 값을 `HandleService.validateForSignup(rawHandle, provider)`가 다음 순서로 검사한다.
  1. 정리: 앞뒤 공백 제거, 소문자로 바꿈(화면에서 이미 소문자지만 서버도 같은 결과를 보장).
  2. 접두어 해석: 값에 `-`가 있으면 첫 `-` 앞부분을 접두어로 본다. 그 앞부분이 알려진 접두어(`go`/`gi`)가 아니면 `HANDLE_INVALID_FORMAT`(예: `kim-min`), 알려진 접두어지만 가입 수단과 다르면 `HANDLE_PREFIX_MISMATCH`(예: 이메일 가입인데 `go-kim`, Google인데 `gi-kim`). 소셜 수단인데 `-`가 없으면 수단 접두어를 서버가 붙인다(마무리 화면은 접두어를 고정 글자로 보여주고 본문만 보낸다).
  3. 형식: `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$` (DB `ck_member_handle`와 같은 문자열을 상수 하나로 둔다) → `HANDLE_INVALID_FORMAT`.
  4. 예약어(본문 기준, 정확히 일치) → `HANDLE_RESERVED` + 대안.
  5. 금칙어(R-9, 본문 기준) → `HANDLE_BANNED_WORD`(대안 없음).
  6. 중복(`existsByHandle`) → `HANDLE_DUPLICATE` + 대안.
  - 화면 확인은 참고용이고, 최종 보장은 `uq_member_handle`이다(R-7).
- **Rationale**: 08 §2 "접두어와 가입 수단의 일치는 가입 Service에서 검사"(`auth_identity`에 수단이 있어 CHECK 불가). 본문에는 `-`가 들어갈 수 없으므로 `-` 유무만으로 접두어를 모호함 없이 해석할 수 있다. 001 web-routes 계약의 매개변수 이름 `handle`을 그대로 쓴다.
- **Alternatives considered**: 소셜 화면이 접두어까지 붙여 보내게 하기 — JS가 꺼진 브라우저에서 사용자가 접두어를 직접 쳐야 한다. 대문자를 거부(오류) — 화면은 이미 소문자로 바꿔 주므로 서버도 같은 정리를 하는 편이 일관된다(DB CHECK는 그대로 최종 방어).

## R-6. 블로그 주소 예약어 비교 방식 (FR-004)

- **Decision**: 블로그 주소 예약어는 **본문과 정확히 일치**할 때만 거부한다(`admin` 거부, `admin_2`·`administrator2` 허용 — 단 `administrator`는 목록에 있어 거부). 목록은 `blog.account.handle.reserved`(application.yml 목록)로 두고 소문자로 비교한다.
- **Rationale**: 08 §3 예시 `admin@x.com → admin_2`가 허용되므로 "포함" 검사가 아니다. 08 §5는 비교 기준을 "본문"으로만 정했다. 닉네임 예약어(포함 검사, R-10)와 다르다.
- **Alternatives considered**: 포함 검사 — `admin_2` 예시와 충돌하고, `blog`·`me`·`user`가 들어간 정상 주소(`kim_blog`, `meadow`)를 대량으로 막는다.

## R-7. 동시 가입·변경의 진 쪽 처리 (FR-010, FR-018, SC-003)

- **Decision**: DB 제약이 최종 보장이다(001 R-8과 같음). 가입·닉네임 변경 Service는 `saveAndFlush`로 제약 위반을 메서드 안에서 드러낸다. 위반(SQLSTATE 23505)은 PostgreSQL 트랜잭션을 중단시키므로, **트랜잭션 경계 바깥**(가입 Service의 바깥 메서드 또는 표현 계층)에서 `MemberUniqueViolationTranslator.translate(e, context)`가 제약 이름으로 다음과 같이 바꾼다.
  - `uq_member_handle` → `HandleTakenException(suggestion)`: 새 읽기 트랜잭션에서 R-3 10단계로 대안을 다시 계산한다. 문구 "방금 다른 분이 이 주소를 사용했어요. `{suggestion}`는 어떠세요?"
  - `uq_member_nickname` → `NicknameViolationException(NICKNAME_DUPLICATE, concurrent = true)`, 문구 "방금 다른 분이 이 닉네임을 사용했어요".
  - **소셜 가입 예외**: 같은 소셜 계정의 마무리를 동시에 두 번 제출하면 `member` INSERT가 먼저 실행되어 `uq_auth_identity`보다 `uq_member_handle`이 먼저 걸릴 수 있다. 번역기는 소셜 가입 문맥이면 먼저 `(provider, provider_user_id)` 계정이 이제 있는지 확인하고, 있으면 001 R-8대로 `ExistingSocialAccount(memberId)`를 돌려 그 계정으로 로그인시킨다.
  - 제약 이름은 51의 이름(`uq_member_handle`, `uq_member_nickname`, `uq_auth_identity`)을 상수로 두고, `ConstraintViolationException.getConstraintName()`으로 읽는다.
- **Rationale**: 애플리케이션 사전 조회만으로는 경쟁 조건을 막지 못한다(헌법 V). 중단된 트랜잭션 안에서는 대안 조회를 할 수 없다. 소셜 이중 제출 처리는 001 R-8의 기대("이미 생긴 계정으로 로그인")를 제약 검사 순서와 무관하게 지키기 위해 필요하다.
- **Alternatives considered**: Redis 분산 락·`SELECT … FOR UPDATE`로 주소 선점 — 없는 행은 잠글 수 없고 실패 지점이 늘어난다. `INSERT … ON CONFLICT DO NOTHING` — JPA 엔터티 저장 흐름과 맞지 않고, 어느 제약에 걸렸는지 구분이 어렵다.

## R-8. 닉네임 정리·형식 (FR-014, FR-015, FR-019)

- **Decision**: `NicknameRules.normalize(raw)` = `strip()`(유니코드 공백 포함 앞뒤 제거) → `Normalizer.normalize(NFC)`. 형식은 `^[가-힣a-zA-Z0-9]{2,10}$`, 글자 포함은 `[가-힣a-zA-Z]` 1자 이상(DB `ck_member_nickname`과 같은 문자열 상수). 허용 문자가 모두 BMP라 `String.length()` = 글자 수다. 보이지 않는 문자·방향 문자·이모지·자모는 형식 단계에서 거부된다(헌법 IV를 별도 제거 없이 만족). 저장값은 정리된 값(NFC)이다.
- **Rationale**: 09 §2와 결정 기록 "닉네임 형식". 정리 후 형식 순서라 NFD `김민서`가 통과한다(US2-2).
- **Alternatives considered**: 보이지 않는 문자를 지운 뒤 통과시키기 — 사용자가 본 값과 저장값이 달라지고, 형식 규칙이 이미 거부하므로 필요 없다.

## R-9. 금칙어 필터 구현 (FR-005, FR-016, SC-006)

- **Decision**: `shared.text.BannedWordFilter.containsBanned(String text)` → `boolean`(걸린 단어는 밖으로 내보내지 않는다).
  - 목록: `classpath:policy/banned-words.txt`, `classpath:policy/banned-words-exceptions.txt`(한 줄 한 단어, `#` 주석, 빈 줄 무시). 위치는 설정값 `blog.text.banned-words.location`, `…exceptions-location`. 시작할 때 NFC + 소문자로 정규화해 불변 집합으로 읽는다. 파일이 없거나 비면 **시작 실패**(조용히 필터가 꺼지는 것을 막는다). 테스트는 테스트용 작은 목록을 쓴다(09 §11 각주).
  - 검사: 입력을 소문자로 바꾼 뒤 4가지 변형 — 그대로 / 숫자 제거 / 숫자→영문(0→o, 1→i, 3→e, 4→a, 5→s, 7→t) / 1→l — 각각에서 **예외 단어를 먼저 지우고**(긴 단어부터, 겹치면 왼쪽부터), 남은 문자열의 모든 부분 문자열을 금칙어 집합에서 찾는다. 닉네임 10자 = 부분 문자열 55개, 주소 본문 36자 = 666개라 목록 크기와 무관하게 빠르다.
  - 블로그 주소에는 **본문**에 적용하고, 본문 그대로와 `_`를 지운 값 두 가지 모두 검사한다(`_`가 닉네임의 공백·특수문자 우회와 같은 역할을 하므로).
  - 거부 문구·오류 응답·로그 어디에도 걸린 단어를 넣지 않는다(로그에는 "banned word matched"와 입력 길이만).
- **Rationale**: 09 §4 그대로. 걸린 단어를 반환하지 않는 API 모양 자체가 SC-006을 보장한다. 03 소개 필터(11 R-3)와 공유하려고 `shared.text`에 둔다.
- **Alternatives considered**: Aho-Corasick 라이브러리 — 입력이 최대 36자라 부분 문자열 조회로 충분하고 의존성이 늘지 않는다. DB 테이블 — 09 §4-1이 운영자 화면 전(Tier C)까지 파일로 정했고 공통 ERD를 바꾸지 않는다(헌법 II).

## R-10. 닉네임 예약어 (FR-017)

- **Decision**: `blog.account.nickname.reserved` 목록(소문자·NFC로 정규화)을 금칙어와 **같은 4가지 변형**에 대해 **포함 검사**한다(예외 목록은 적용하지 않음). `BannedWordFilter`의 변형 생성 로직을 `TextVariants` 유틸로 공유한다.
- **Rationale**: 09 §5 "포함만 해도 쓸 수 없다, 대소문자 무시, 금칙어와 같은 변형 4가지". 예외 목록은 금칙어(비속어 오탐)용이라 예약어에는 쓰지 않는다.
- **Alternatives considered**: 예약어를 금칙어 파일에 합치기 — 오류 코드(`NICKNAME_RESERVED` vs `NICKNAME_BANNED_WORD`)와 블로그 주소의 정확 일치 규칙이 달라서 섞을 수 없다.

## R-11. 닉네임 중복과 자기 자신 제외 (FR-018, FR-024)

- **Decision**: 중복 검사는 `existsByNicknameIgnoreCase(normalized, excludeMemberId)` = `lower(nickname) = lower(:n) AND id <> :exclude`(`uq_member_nickname` 함수 인덱스 사용). 탈퇴 유예 중인 회원의 닉네임은 행에 그대로 있으므로 자동으로 묶이고, 익명 처리(`nickname = NULL`) 뒤에는 자동으로 풀린다(13 D-8, NULL은 UNIQUE에서 제외). 동시성은 R-7.
- **Rationale**: 51 `uq_member_nickname`이 대소문자 무시 유일을 이미 보장한다. 탈퇴 묶기·해제를 위한 별도 로직이 필요 없다(FR-026).
- **Alternatives considered**: 애플리케이션에서 `nickname_lower` 컬럼 관리 — 공통 ERD 변경이 필요하다(헌법 II).

## R-12. 닉네임 변경 30일 제한 (FR-023~FR-025, SC-007)

- **Decision**: `NicknameChangeService.change(memberId, rawNickname)`(현재 트랜잭션에 참여, `REQUIRED`).
  1. `member` 행을 `PESSIMISTIC_WRITE`(`SELECT … FOR UPDATE`)로 읽는다 — 같은 회원의 동시 변경 두 건이 둘 다 30일 검사를 통과하는 것을 막는다.
  2. 정리값이 현재 닉네임과 **글자까지 같으면** 아무것도 하지 않고 `UNCHANGED`(30일 제한 시작 안 함, 제한 중이어도 성공).
  3. `nickname_changed_at`이 있고 `now < nickname_changed_at + 30일`이면 `NicknameChangeTooSoonException(nextAllowedAt)` → 409 `NICKNAME_CHANGE_TOO_SOON`.
  4. `NicknamePolicy.validate(raw, excludeMemberId = memberId)` — 대소문자만 바꾼 경우(`kim`→`Kim`) 자기 자신은 중복에서 빠진다.
  5. `nickname = 정리값`, `nickname_changed_at = now`, `updated_at = now`; `saveAndFlush`.
  - 가입 시에는 `nickname_changed_at = NULL`(가입 닉네임은 변경으로 세지 않음). 30일은 `blog.account.nickname.change-cooldown=30d` 설정값, 시각은 주입한 `Clock`.
  - 다음 변경 가능일 표시는 `NicknameChangeService.nextAllowedAt(memberId)`(없으면 지금 변경 가능). 화면 날짜는 `Asia/Seoul` 기준 날짜로 보여준다.
  - 권한: 호출자는 `CurrentUser`의 memberId만 넘긴다(요청 값으로 받지 않음). 42 §9에 따라 인증 전 회원도 변경할 수 있으므로 `AccountGuard.requireLoggedIn`만 쓴다(탈퇴 유예는 001의 복구 전용 세션 필터가 이미 막는다).
- **Rationale**: 09 §8, 11 §5(제한 중 닉네임 변경이 들어 있으면 전체 실패 — 예외가 003 트랜잭션 전체를 롤백한다). 행 잠금은 한 회원 범위라 경합이 거의 없다.
- **Alternatives considered**: 조건부 `UPDATE … WHERE nickname_changed_at <= now - 30d` — 원자적이지만 003이 같은 트랜잭션에서 JPA 엔터티로 소개·사진을 함께 바꾸는 흐름과 섞이면 영속성 컨텍스트와 어긋난다. 낙관적 잠금 — `member`에 버전 컬럼이 없다(공통 ERD 변경 필요).

## R-13. 소셜 이름으로 닉네임 미리 채우기 (FR-022)

- **Decision**: `NicknamePolicy.suggestFromSocialName(displayName)` = NFC → 허용 문자(`가-힣a-zA-Z0-9`) 외 전부 제거 → 앞에서 10자 자르기 → `validate`(중복 포함) 통과 시 그 값, 아니면 빈 값(화면 "닉네임을 입력해 주세요"). 소셜 이름 출처: Google은 OIDC `name`, GitHub은 `name`, GitHub `name`이 비어 있으면 `login`을 쓴다. 이메일 가입은 미리 채우지 않는다.
- **Rationale**: 09 §7 예시(`Kim Min-seo`→`KimMinseo`, `Christopher Columbus`→`Christophe`, `A`→빈칸, 이미 있는 `Kim`→빈칸)를 그대로 재현한다. GitHub는 `name`을 비워 둔 사용자가 많아 `login`이 가장 가까운 "소셜 이름"이다(식별에는 쓰지 않으므로 07 §5와 충돌하지 않음).
- **Alternatives considered**: GitHub `name`이 없으면 빈칸 — 사용자가 매번 직접 입력해야 한다. 미리 채운 값이 중복이면 `2`를 붙이기 — 09 §7 예시("`Kim` 이미 사용 중 → 빈칸")와 다르다.

## R-14. 사용 가능 여부 API와 요청 제한 (FR-009, FR-021, SC-008)

- **Decision**: 
  - `GET /api/handles/availability?handle=…` → `{ available, reason, suggestion }`, `GET /api/nicknames/availability?nickname=…` → `{ available, code }`(08 §4-2, 09 §6 모양 그대로). 로그인 불필요(가입 전·소셜 마무리 화면에서 쓰임). 상태를 바꾸지 않으므로 CSRF 대상 아님.
  - 요청 제한: 001의 `RedisRateLimiter`(Lua `INCR`+`EXPIRE`)를 재사용, 키 `account:handle-check:ip:{ip}`, `account:nickname-check:ip:{ip}`, 각 1분 30회(`blog.account.availability.per-ip-per-minute=30`). `POST /api/handles/suggestion`은 주소 버킷을 함께 쓴다. 초과하면 **429 `RATE_LIMITED`** + `Retry-After`(초).
  - 닉네임 확인은 로그인한 회원이 부르면 자기 자신을 중복에서 제외한다(프로필 화면에서 지금 닉네임의 대소문자만 바꿀 때 "사용 중"이 뜨지 않게).
  - 화면: 입력이 0.5초 멈추면 호출(`static/js/account/availability.js`), 이전 요청은 `AbortController`로 취소. 서버 처리는 인덱스 조회 1~2회.
- **Rationale**: 08 §4-2, 09 §6. 클라이언트 IP는 001 R-6과 같이 신뢰 프록시의 `X-Forwarded-For`만 반영한다.
- **429 선택 이유와 한계**: 42 §4의 응답 코드 표(401/403/404/400/409)에는 요청 제한 코드가 없다. 001은 화면 문구로만 처리했지만, 이 API는 JSON을 받는 JS가 "확인 불가"와 "사용 중"을 구분해야 하므로 HTTP 표준 429를 쓴다. 42 표에 추가할지는 팀 확인 사항(U-2).
- **Alternatives considered**: 200 + `available: null, reason: RATE_LIMITED` — 42 표 안에 머물지만 "성공 응답인데 결과 없음"이라 의미가 흐리다. 400 — 요청 자체는 규칙에 맞다.

## R-15. 블로그 주소로 접속 (FR-012, FR-013)

- **Decision**: 
  - `shared.web.HandlePathCanonicalizer`(서블릿 필터, `OncePerRequestFilter`)가 `/@{handle}`와 `/@{handle}/**` 경로에서 handle 부분에 대문자가 있으면 그 부분만 소문자로 바꿔 **301**로 보낸다(나머지 경로·쿼리 유지). 존재 여부를 보기 전에 실행한다(08 §6, 40 상세 ①과 같은 순서).
  - 소문자 handle이 형식(`ck_member_handle` 정규식)에 맞지 않거나(`/@없는주소` 포함) 주인이 없으면 404. 주인 찾기는 `BlogOwnerResolver.resolve(handle)` → `Optional<BlogOwner>`이고 **`status = 'WITHDRAWN'`(유예 중·익명 처리 모두)이면 비어 있음** → 공통 404 화면(42 §9 "탈퇴 유예·익명 처리된 주소는 404", 헌법 III 404 통일).
  - 블로그 페이지 본문(글 목록·프로필 상단)은 009·003이 그린다. 002는 주소 정규화·주인 찾기·`닉네임 @주소` 표시 조각까지만 맡는다.
  - 탈퇴한 계정의 주소는 회원 행이 영구히 남으므로(13 D-9·D-10, `handle` 보존) `uq_member_handle`이 재사용을 자동으로 막는다. 별도 "사용된 주소" 목록은 두지 않는다.
- **Rationale**: 경로 정규화를 한곳(필터)에 두면 블로그·글 상세·향후 하위 경로가 같은 규칙을 쓴다. 정지 회원의 블로그 노출은 42에 규정이 없어 이 기능은 막지 않는다(43 범위).
- **Alternatives considered**: 컨트롤러마다 대문자 검사 — 경로가 늘 때마다 빠뜨릴 수 있다. 대소문자 무시 조회 후 200 — 08 §6(301)과 다르고, 같은 글이 여러 주소로 색인된다.

## R-16. `닉네임 @블로그주소` 표시 (FR-027)

- **Decision**: `AuthorDisplay(handle, nickname, withdrawn)` 값 객체와 Thymeleaf 조각을 둔다.
  - `fragments/author :: byline(author)` → `김민서 @kim755030`(주소를 누르면 `/@kim755030`) — 글 상세 작성자 영역, 댓글, 블로그 상단.
  - `fragments/author :: name(author)` → 닉네임만 — 글 카드.
  - `withdrawn = (withdrawn_at IS NOT NULL)`이면 두 조각 모두 "탈퇴한 사용자"만 보이고 링크·`@주소`를 내지 않는다(익명 처리 후 `nickname = NULL`도 같은 표시).
  - 출력은 Thymeleaf `th:text`(이스케이프)만 쓴다(헌법 IV).
  - 목록·댓글 조회(009·014)는 이미 각자 읽기 쿼리에서 `m.handle, m.nickname, m.withdrawn_at`을 함께 가져오므로(10 §7, 21 §7), 그 값으로 `AuthorDisplay.of(…)`만 만든다. 다른 모듈이 회원 정보를 따로 필요로 하면 `MemberSummaryQuery.findByIds(ids)`(한 번에 묶어 조회, N+1 금지)를 쓴다.
- **Rationale**: 09 §9, 13 "탈퇴한 사용자". 표시 규칙을 한곳에 둬서 기능마다 탈퇴자 처리가 갈라지지 않게 한다.
- **Alternatives considered**: 각 템플릿이 직접 조합 — 탈퇴자 처리·링크 규칙이 갈라진다.

## R-17. 스키마 — 공통 ERD(51) 그대로

- **Decision**: 새 테이블·컬럼·인덱스·마이그레이션을 만들지 않는다. `member.handle`(`uq_member_handle`, `ck_member_handle`), `member.nickname`(`uq_member_nickname` lower 인덱스, `ck_member_nickname`, `ck_member_nickname_null`), `member.nickname_changed_at`을 51 정의대로 쓴다. 금칙어·예약어는 DB 제약으로 표현하지 않는다(09 §10).
- **52 영향**: 52의 A/B/C 항목 중 `handle`·`nickname`·`nickname_changed_at`을 건드리는 것은 없다. A-3(`SUSPENDED` 삭제)은 `BlogOwnerResolver`가 `WITHDRAWN`만 걸러서 영향 없다. C-1(`profile_image_url`)은 `BlogOwner`가 값을 읽기만 하므로 컬럼이 빠지면 `image` 조인으로 바뀌는 한 곳만 영향받는다.
- **Rationale**: 헌법 II, 지시사항 "51·52 정의를 그대로".

## R-18. 테스트 전략 (헌법 VI)

- **Decision**: 
  - 단위: `HandleRulesTest`(08 §3 예시 12개 + 08 §2 형식표 매개변수 테스트, 난수 고정), `NicknameRulesTest`(09 §2~§5 예시), `BannedWordFilterTest`(4가지 변형·예외·주소 `_` 제거, 테스트 목록 사용), `TextVariantsTest`.
  - 통합(Testcontainers PostgreSQL 18 + Redis): 10단계 번호 붙이기(실제 행으로 `kim755030`, `kim755030_2` …), 가입 시 접두어 불일치 거부, 동시 20건 같은 주소/같은 닉네임(대소문자만 다름 포함) → 성공 1건(SC-003), 닉네임 변경 30일(`Clock` 조정), 같은 값 재저장·대소문자만 변경, 탈퇴 유예 닉네임 묶임·익명 처리 후 해제, `/@Kim755030` 301·`/@없는주소` 404·탈퇴 회원 주소 404, 사용 가능 여부 API 응답과 1분 30회 제한, 거부 응답 본문에 금칙어 문자열이 없음(SC-006).
  - 001과 같은 테스트 기반(`@SpringBootTest` + Testcontainers 공용 설정)을 쓴다.
- **Rationale**: 수용 시나리오를 그대로 테스트로 옮긴다. 소유 검사(닉네임 본인만 변경)는 통합 테스트 필수(헌법 VI).
- **Alternatives considered**: H2 — 헌법 VI 금지, `lower()` 함수 인덱스·정규식 CHECK 동작이 다르다.

---

## 남은 확인 사항 (계획 진행을 막지 않음)

| # | 항목 | 현재 선택 | 확인 주체 |
|---|---|---|---|
| U-1 | 서비스 이름 확정 후 두 예약어 목록에 추가 | 설정값 갱신만으로 반영(코드 변경 없음) | 팀 (서비스 이름 결정 시) |
| U-2 | 사용 가능 여부 API의 요청 제한 초과 응답 코드 | 429 `RATE_LIMITED` + `Retry-After` (42 §4 표 밖) | 팀 확인 권장 (42 표에 추가 여부) |
| U-3 | 금칙어·예외 목록의 실제 내용 | 공개 목록을 팀이 검토해 `policy/banned-words*.txt`로 커밋. 그 전에는 테스트용 목록 | 팀 (09 §4-1) |
| U-4 | 주소 미리 채우기용 `POST /api/handles/suggestion` | 08 원문에 없는 보조 API를 추가(R-4) | 팀 확인 권장 |
| U-5 | GitHub `name`이 비었을 때 `login`으로 닉네임 미리 채우기 | `login` 사용(R-13) | 원문 미정, 합리적 기본값 |
| U-6 | 블로그 주소 금칙어 검사 시 `_` 제거 변형 추가 | 본문 그대로 + `_` 제거 둘 다 검사(R-9) | 원문 미정, 보수적 선택 |

---

## 구현 메모 (/speckit-implement, 2026-10-07)

tasks.md와 다르게 하거나 tasks.md에 없는 세부를 정한 곳. 모두 계약(contracts/)의 동작은 그대로다.

| # | 내용 | 이유 |
|---|---|---|
| I-1 | `MemberUniqueViolationTranslator.SignupContext`에 `handle`(제출한 주소) 필드를 추가했다(`email(handle)`, `social(provider, providerUserId, handle)`). | `uq_member_handle` 위반 예외에는 어떤 값이 걸렸는지 믿을 만한 형태로 들어 있지 않아, 대안 주소의 기준을 호출자가 넘긴다. 001 T161이 같은 모양으로 호출한다. |
| I-2 | `CurrentUser.role`은 `String`(`USER`/`ADMIN`)이다. | `shared.security`가 `account.domain.Role`에 기대지 않게 했다. 001이 필요하면 `Role.valueOf(role)`로 바꾼다. |
| I-3 | `Handle`·`HandlePrefix`·`Nickname`과 형식 상수(`HandleRules.FORMAT`, `NicknameRules.FORMAT`/`LETTER`)를 Phase 2에서 먼저 만들었다(T045·T048·T064 일부). | T028 `Member(Handle, Nickname, Instant)` 생성자가 이 값 객체를 쓴다. |
| I-4 | 금칙어 필터는 예외 단어를 "지운" 자리를 경계로 둔다(앞뒤 조각을 이어 붙여 새 부분 문자열을 만들지 않음). | `시발점` 같은 예외를 지운 뒤 앞뒤 글자가 이어져 생기는 오탐을 막는다. 예외 밖의 금칙어(`시발점병신`)는 그대로 차단된다. |
| I-5 | `HandleService.prefill(email, provider, RandomGenerator)` 오버로드를 공개했다. | 08 §3 8단계(`user_483920`) 예시를 통합 테스트에서 그대로 재현하기 위함. 운영 경로는 `SecureRandom`을 쓰는 2인자 메서드다. |
| I-6 | `compose.yaml`의 호스트 포트를 `${DB_PORT:-5432}`, `${REDIS_PORT:-6379}`, `${MAIL_PORT:-1025}`, `${MAILPIT_WEB_PORT:-8025}`로 바꿀 수 있게 했다(기본값은 tasks 그대로). Redis 이미지는 `redis:8`. | 같은 기계의 다른 프로젝트가 기본 포트를 쓰고 있어도 띄울 수 있게. |
| I-7 | `build.gradle.kts`의 테스트 태스크는 `DOCKER_HOST`가 없고 Colima 소켓(`~/.colima/default/docker.sock`)이 있으면 그 소켓을 Testcontainers에 알려 준다. | `/var/run/docker.sock`이 없는 Colima 환경에서 Testcontainers가 Docker를 찾지 못했다. 다른 환경에는 영향 없음. |
| I-8 | `GlobalExceptionHandler`가 `NoResourceFoundException`(경로 없음)도 `NotFoundException`과 같은 404 화면으로 그린다. | 없는 경로·없는 주소·탈퇴 주소의 404 본문을 똑같이 맞춘다(헌법 III). |
| I-9 | `AvailabilityLatencyIT`의 p95는 MockMvc(서버 처리 시간) 기준이다. 요청 제한에 걸리지 않게 요청마다 원격 IP를 바꾼다. | SC-008은 서버 응답 시간 기준. |
| I-10 | 가입 화면 마크업(001 T134·T163)이 붙기 전이라 화면 JS(`handle-field.js`, `availability.js`)와 quickstart 수동 항목(S1-2~4, S2-5, S7-2)은 브라우저로 확인하지 않았다. | 001 Polish(T175)에서 함께 확인한다(tasks T087). |
