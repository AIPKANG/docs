# Research: 로그인·로그아웃 (001-auth)

**Phase 0 산출물** · 작성일 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `.specify/memory/constitution.md`, `docs/07-auth.md`, `docs/02-architecture.md`, `docs/01-common-requirements.md` §4(결정 기록, 우선), `docs/42-permission-matrix.md`, `docs/51-erd-unified.md`, `docs/52-erd-review.md`

각 항목은 **Decision / Rationale / Alternatives considered** 형식이다. plan.md Technical Context의 미정 항목은 모두 여기서 정했다.

---

## R-1. 표현 계층 — Thymeleaf SSR + 세션 폼 로그인

- **Decision**: Thymeleaf 서버 렌더링 + Spring Security 폼 로그인 + Spring Session(Redis) 세션. CSRF는 Spring Security 기본값(켜짐) 유지. (사용자 확정 2026-10-07)
- **Rationale**: 01 Q2에서 팀원 2명이 SSR+세션을 고른 다수안이다. 07 §6이 기본 경로로 적은 방식이고, 세션 고정 방지·CSRF·로그아웃 세션 삭제가 프레임워크 기본값으로 해결된다. 업무 규칙은 Service에 두므로(헌법 I) REST 표현 계층을 쓰는 팀원도 같은 Service를 재사용한다.
- **Alternatives considered**: REST + SPA + JWT(Access + Refresh 토큰 Redis 저장·회전) — 개인 선택으로 허용되나 이 계획의 기본값은 아니다. 로그아웃·재설정 무효화를 위해 Refresh 토큰 저장소를 따로 만들어야 해서 구현량이 늘어난다.

## R-2. Spring Boot 버전

- **Decision**: **Spring Boot 4.1.1** (Java 21). **팀 확정 대기** — 헌법·01 Q8은 "버전은 팀 확정"으로 남겨 두었고, 문서 중 구체 버전은 02 §2의 "김민서 문서는 Spring Boot 4.1.1 기준" 하나뿐이다.
- **Rationale**: 문서에 근거가 있는 유일한 버전이다. Boot 4 계열이면 Spring Security 7, Spring Framework 7, Spring Session 4 계열을 쓴다.
- **유의점(설계 수준)**: Boot 4는 스타터·자동 설정 모듈이 재편되었다(예: OAuth2 Client·Session Redis·Flyway 스타터 이름). 실제 의존성 좌표는 구현 시작 시 4.1.1 BOM으로 확인한다. Spring Security 7의 설정은 람다 DSL만 쓴다.
- **Alternatives considered**: Spring Boot 3.5.x — 자료가 많지만 문서 근거가 없다. 팀이 3.x로 정하면 설계는 그대로이고 의존성 좌표만 바뀐다.

## R-3. 세션 저장과 14일 유지 (FR-024)

- **Decision**: Spring Session Data Redis의 **인덱스 저장소**(`RedisIndexedSessionRepository`, 설정 `repository-type=indexed`)를 쓴다. 세션 최대 비활성 시간 14일(설정값 `blog.auth.session-timeout=14d`). 세션 쿠키는 `HttpOnly`·`Secure`·`SameSite=Lax`, `Max-Age` 14일. 쿠키도 마지막 활동 기준으로 연장되도록 **하루 한 번 쿠키를 다시 내려 주는** 작은 필터를 둔다(서버 세션은 요청마다 연장되지만 Spring Session은 세션 ID가 바뀔 때만 쿠키를 쓰기 때문).
- **Rationale**: "모든 기기 로그아웃"(FR-019, 정지 시 세션 삭제 42 P-7)에는 회원별 세션 목록 조회가 필요하다. 인덱스 저장소만 `FindByIndexNameSessionRepository`(principal 이름 인덱스)를 지원한다. principal 이름은 `memberId` 문자열로 고정해 이메일 변경과 무관하게 한다. Redis 설정(AOF `everysec`, `noeviction`)은 헌법 기술 제약을 따른다.
- **Alternatives considered**: (a) 기본 `RedisSessionRepository` — 회원별 세션 조회가 없어 모든 기기 로그아웃 불가. (b) Remember-me 쿠키 — 세션과 별도 토큰 저장소가 생기고 무효화 지점이 둘이 된다. (c) 브라우저 세션 쿠키(Max-Age 없음) — 브라우저를 닫으면 로그인이 풀려 14일 유지가 성립하지 않는다.

## R-4. 비밀번호 저장과 규칙 (FR-012~FR-015)

- **Decision**: `DelegatingPasswordEncoder`의 BCrypt(강도 10, `{bcrypt}` 접두어)로 `auth_identity.password_hash`(VARCHAR(100))에 저장. 규칙 검사는 `account.domain.PasswordPolicy` 하나에서 한다: 길이 8~16, 영문 1+·숫자 1+·특수문자 1+, 허용 문자 = 영문·숫자·07 §4 특수문자 목록(공백·한글·그 밖의 문자 거부), 이메일 `@` 앞부분 포함 금지(대소문자 무시, 앞부분이 3자 이상일 때 적용), 흔한 비밀번호 목록(클래스패스 리소스, 대소문자 무시 비교) 금지. 가입·재설정·변경(003)이 같은 정책을 호출한다. 화면의 규칙별 ✓ 표시는 같은 규칙을 JS로 보여주는 안내일 뿐이고 서버가 최종 판정한다.
- **Rationale**: 07 §4와 결정 기록 "비밀번호"를 그대로 옮겼다. 16자 상한은 BCrypt 72바이트 한계와 충돌하지 않는다. 접두어 덕분에 나중에 알고리즘을 바꿔도 기존 해시를 읽을 수 있다.
- **이메일 앞부분 3자 기준**: 원문은 길이 조건이 없다. `ab@x.com`처럼 아주 짧은 앞부분은 무관한 비밀번호까지 막으므로 3자 미만은 검사에서 뺐다. 팀 확인이 필요한 세부값이라 설정값(`blog.auth.password.local-part-min-length=3`)으로 둔다.
- **Alternatives considered**: Argon2 — 결정 기록이 BCrypt로 확정했다. 외부 유출 비밀번호 API(HIBP) — 외부 호출과 개인정보 문제로 제외.

## R-5. 인증·재설정 토큰 (FR-008~FR-010, FR-017)

- **Decision**: `SecureRandom` 32바이트 → Base64 URL-safe(패딩 없음) 문자열을 메일 링크에 넣는다. Redis에는 **토큰의 SHA-256 해시**를 키로 쓴다: `auth:verify:{sha256}` = memberId(TTL 24h), `auth:reset:{sha256}` = memberId(TTL 30m). 회원별 현재 토큰 포인터 `auth:verify-current:{memberId}`, `auth:reset-current:{memberId}`를 같은 TTL로 두어 새 토큰을 발급하면 이전 토큰 키를 지운다(재발송 시 이전 링크 무효, FR-009). 사용은 `GETDEL`로 원자적으로 꺼내 한 번만 성공하게 한다.
- **Rationale**: 07 §3·§4-1의 키 구조와 TTL을 따르되, FR-014("인증 토큰을 저장하거나 로그에 남기지 않는다")를 만족하려고 원문 대신 해시를 키로 둔다. Redis 덤프가 유출돼도 링크를 만들 수 없다. 토큰 엔트로피가 256비트라 솔트 없는 해시로 충분하다.
- **재설정 링크 재요청**: 원문은 정하지 않았다. 인증 링크와 같이 **새 링크를 보내면 이전 재설정 링크는 무효**로 한다(보수적 선택).
- **Alternatives considered**: DB 토큰 테이블 — 07 §8이 "테이블 대신 Redis(TTL)"로 정했고 공통 ERD를 바꾸지 않는다(헌법 II). 원문 토큰을 키로 저장 — FR-014와 어긋난다.

## R-6. 요청 횟수 제한 (FR-009, FR-018, FR-025)

- **Decision**: Redis 카운터(`INCR` + 첫 증가 시 `EXPIRE`를 Lua 스크립트 하나로 원자 처리)를 `RateLimiter` 컴포넌트로 감싼다. 키와 수치는 [contracts/redis-keys.md](./contracts/redis-keys.md)에 정리했고 수치는 모두 설정값이다.
  - 인증 메일 재발송: `auth:verify-resend:{memberId}:min`(1분 1회), `auth:verify-resend:{memberId}:{yyyyMMdd}`(하루 10회, 07 §3 키).
  - 비밀번호 찾기: 이메일 해시별 1분 1회·하루 10회, IP별 1시간 20회. 초과해도 화면 문구는 같다.
  - 로그인: 정규화한 이메일(해시)별 연속 실패 카운터(TTL 15분, 실패마다 연장, 성공 시 삭제) → 5회째에 `auth:login-lock:{emailHash}`(TTL 15분). IP별 1분 20회.
- **Rationale**: 07 §6 "Redis 카운터". 여러 서버에서도 같은 값을 본다.
- **가입 여부 비노출**: 잠금은 **존재하지 않는 이메일에도 똑같이** 적용한다. 없는 계정의 비밀번호 확인도 Spring Security `DaoAuthenticationProvider`의 더미 해시 비교로 시간 차이를 줄인다(SC-006).
- **클라이언트 IP**: `server.forward-headers-strategy`로 신뢰하는 프록시의 `X-Forwarded-For`만 반영한다. 배포 프록시 구성은 환경 확인 시 정한다.
- **Alternatives considered**: Bucket4j 등 라이브러리 — 규칙이 단순한 고정 창 카운터라 직접 구현이 더 작다. 메모리 카운터 — 서버를 늘리면 값이 갈라진다.

## R-7. 소셜 로그인 흐름 (FR-020~FR-023, FR-033)

- **Decision**: Spring Security `oauth2Login()` + 사용자 정의 `OAuth2UserService`/`OidcUserService`. `state` 검증은 프레임워크 기본값(FR-020), Google은 OIDC로 `sub`·`email`·`email_verified`를, GitHub은 `read:user user:email` 스코프로 숫자 `id`와 `/user/emails`의 `primary && verified` 이메일을 얻는다.
  - `(provider, provider_user_id)` 계정이 있으면 → 계정 상태 검사(R-9) 후 로그인.
  - 없으면 → **로그인하지 않고** `PendingSocialSignup`(provider, providerUserId, 인증된 이메일 또는 없음, 표시 이름, 사진 주소, 생성 시각)을 세션 속성에 넣고 `/signup/social`로 보낸다. 10분이 지나면 무효(FR-021).
  - 마무리 제출 → `SocialSignupService`가 한 트랜잭션에서 `member` + `auth_identity`(+ `member_agreement` 2행) 생성. 이메일이 인증된 경우 `email_verified_at = 가입 시각`(FR-022), GitHub에 인증된 이메일이 없어 사용자가 입력한 경우 `email_verified_at = null`로 만들고 R-5 인증 메일을 보낸다.
  - **같은 이메일 안내(FR-033, 사용자 확정 2026-10-07)**: 인증된 이메일이 있을 때만 `ix_auth_identity_email`로 다른 수단의 계정이 있는지 조회해 마무리 화면에 "이 이메일로 가입한 계정이 이미 있어요. [기존 계정으로 로그인] [새 계정 만들기]"를 보여준다. [기존 계정으로 로그인]은 `PendingSocialSignup`을 지우고 로그인 화면으로 보낸다. 계정을 합치거나 연결하지 않는다(L-1). 안내는 그 이메일의 인증된 주인에게만 보이므로 가입 여부가 남에게 드러나지 않는다. 탈퇴 유예·익명 처리된 계정은 안내 대상에서 뺀다.
  - 프로필 사진(FR-023): 마무리 화면에서 "프로필 사진 사용" 체크 시, 계정 생성 직후 브라우저가 사진을 받아 256×256으로 바꿔 업로드한다(11 §4-2). 이 기능은 사진 주소를 화면에 넘기고 003-profile/media의 업로드 흐름을 호출하는 데까지만 맡는다.
- **Rationale**: 07 §5. 마무리 전에 계정을 만들지 않아 닉네임 규칙 위반 계정이 생기지 않는다.
- **Alternatives considered**: 콜백에서 바로 임시 계정을 만들고 나중에 채우기 — `ck_member_nickname_null` 때문에 닉네임 없는 회원을 만들 수 없고, 버려진 계정 정리가 필요하다.

## R-8. 같은 로그인 수단의 중복 가입 방지 (FR-002, SC-001)

- **Decision**: DB 제약이 최종 보장이다. 이메일 가입은 `uq_auth_identity (provider='LOCAL', provider_user_id=소문자 이메일)`, 소셜은 `(provider, provider_user_id)`. 동시 요청에서 제약 위반(SQLSTATE 23505)을 잡아 이메일 가입이면 FR-007 안내로, 소셜이면 이미 생긴 계정으로 로그인시킨다. 블로그 주소·닉네임 충돌(`uq_member_handle`, `uq_member_nickname`)은 각각 002 규칙(주소는 `_2`, `_3`…, 닉네임은 오류 안내)으로 처리한다.
- **Rationale**: 애플리케이션 사전 조회만으로는 경쟁 조건을 막지 못한다. 헌법 V "같은 요청을 여러 번 보내도 결과는 한 번".
- **Alternatives considered**: Redis 분산 락 — DB 제약으로 충분하고 실패 지점이 늘어난다.

## R-9. 로그인 시 계정 상태 처리 (FR-030, 42 P-7·P-12)

- **Decision**: 폼·소셜 공통 `AccountStatusChecker`를 인증 성공 직후(비밀번호가 맞은 뒤)에 실행한다.
  - **정지 판정은 `member_suspension`으로 한다**: `lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now())`인 행이 있으면 로그인 거부 + `ACCOUNT_SUSPENDED` + "정지된 계정이에요 (~기한, 사유)". 51 기준으로 `member.status = 'SUSPENDED'`인데 기간이 끝났으면 `lifted_at` 기록 + `status = 'ACTIVE'`로 되돌린다(51 member_suspension 설명). 이렇게 하면 52 A-3(“`SUSPENDED` 삭제” 제안, 팀 결정 대기)이 채택되어도 판정 코드는 바뀌지 않고 되돌리기 단계만 빠진다.
  - 탈퇴 유예(`status = 'WITHDRAWN' AND deleted_at IS NULL`) → 로그인은 되지만 세션에 "복구 전용" 표시를 남기고, 필터가 복구 화면·로그아웃 외 모든 요청을 복구 화면으로 보낸다(SSR). 복구 동작 자체는 회원 탈퇴 기능(13·44) 범위다.
  - 정지·탈퇴 안내는 **비밀번호가 맞은 경우에만** 보여준다. 틀리면 FR-026 동일 메시지(가입 여부 비노출).
- **Rationale**: 42 §3 판정 순서와 P-7·P-12, 52 A-3의 문제(기간이 지난 `SUSPENDED` 잔존)를 피한다.
- **Alternatives considered**: `member.status`만 보고 판정 — 52 A-3 문제 1(상태 잔존)을 그대로 안는다.

## R-10. 인증 전 회원의 쓰기 차단 (FR-011, FR-032, 헌법 III)

- **Decision**: `shared.security`에 `CurrentUser`(memberId, role)와 `AccountGuard.requireWritable(currentUser)`를 둔다. 쓰기 Service(글·댓글·사진·좋아요·신고)가 메서드 첫 줄에서 호출한다. 판정은 42 §3 순서: 비회원 → `LoginRequiredException`(401, SSR은 로그인 화면으로), 탈퇴 유예 → 403 `ACCOUNT_WITHDRAWN`, 인증 전 → 403 `EMAIL_NOT_VERIFIED`. 인증 여부는 세션 값이 아니라 `auth_identity.email_verified_at`을 PK 조회로 읽는다(다른 기기에서 인증해도 즉시 반영). 자기 글·댓글 삭제·복구 Service는 `requireLoggedIn`만 호출한다.
- **Rationale**: 헌법 III "서버(Service)에서 다시 검사", 42 P-5·P-6. 화면 숨김은 보조.
- **Alternatives considered**: URL 패턴 기반 `authorizeHttpRequests`만으로 차단 — 표현 계층마다 다시 써야 하고 REST 팀원과 규칙이 갈라진다. URL 규칙은 1차 방어로만 둔다.

## R-11. 로그아웃과 브라우저 임시 데이터 삭제 (FR-031, SC-008)

- **Decision**: 로그아웃은 `POST /logout`(CSRF 토큰 포함). 서버는 세션 무효화 + 쿠키 삭제 후 `/`로 이동. 브라우저 정리는 두 겹: ① 로그아웃 버튼 스크립트(`auth-logout.js`)가 제출 전에 IndexedDB의 `draft:{memberId}:*`, `draft-backup:{memberId}:*`를 지운다(04 §2-2). ② 실패 대비로 로그아웃 후 첫 홈 화면에 1회용 플래시 값(memberId)을 넘겨 같은 정리를 한 번 더 실행한다. JS가 꺼진 브라우저는 자동 저장 자체가 동작하지 않으므로 지울 데이터가 없다.
- **Rationale**: 결정 기록 "공용 PC". memberId 범위만 지워 같은 브라우저의 다른 계정 데이터는 건드리지 않는다.
- **Alternatives considered**: `Clear-Site-Data: "storage"` 헤더 — 같은 출처의 모든 저장소(다른 계정의 동기화 전 임시글 포함)를 지워 범위가 넓다. 필요하면 개인 확장으로.

## R-12. 로그인 후 이동 주소 (FR-027)

- **Decision**: 보호된 페이지 접근 시 Spring Security의 저장된 요청을 쓰고, 로그인 화면의 `redirect` 값은 `RedirectTargetValidator`로 검사한다: `/`로 시작, `//`·`/\`로 시작하지 않음, 스킴·호스트 없음, 제어 문자 없음. 통과하지 못하면 `/`.
- **Rationale**: 07 §6 "상대 경로만"(나민서 D-14), 열린 리다이렉트 방지.
- **Alternatives considered**: 허용 호스트 목록 — 우리 사이트만 쓰므로 상대 경로 규칙이 더 단순하다.

## R-13. 메일 발송 (L-9)

- **Decision**: `MailSender` 포트 + Spring `JavaMailSender` 어댑터. 개발·테스트는 Mailpit(SMTP 1025, 웹 API 8025), 운영 SMTP는 환경 확인 후 결정(설정만 바꿈). 메일은 **커밋 후** 보낸다(`@TransactionalEventListener(AFTER_COMMIT)`로 `VerificationMailRequested`·`PasswordResetMailRequested` 처리). 발송 실패는 로그(토큰·주소 마스킹)만 남기고 가입·요청 결과를 바꾸지 않는다. 사용자는 재발송으로 회복한다. 메일 본문은 Thymeleaf 텍스트·HTML 템플릿.
- **Rationale**: 헌법 V "트랜잭션 안에서 외부 호출을 하지 않는다", 부가 처리 실패가 핵심을 막지 않는다.
- **Alternatives considered**: RabbitMQ 유실 없는 발송 — 02 §2에서 개인 확장으로 둠.

## R-14. 스키마 — 공통 ERD(51) 그대로 사용

- **Decision**: 새 테이블·컬럼·마이그레이션을 만들지 않는다. `member`, `auth_identity`, `member_agreement`, `member_suspension`(읽기·자동 해제)을 51 정의대로 쓴다.
  - **약관 동의는 `member_agreement` 행(`TERMS`, `PRIVACY`)으로 저장한다.** 07 §3·§8과 source-notes에는 `member.terms_agreed_at`·`privacy_agreed_at`가 적혀 있지만, 51(2026-10-06 ERD Cloud)이 이 컬럼을 `member_agreement`로 옮겼고 이 계획은 51을 따른다. "동의 없이 가입 불가"는 DB가 아니라 가입 트랜잭션이 보장한다(51 §4).
  - 이메일 가입 행은 `ck_auth_local_email`(소문자 이메일 = 식별값), `ck_auth_password`(LOCAL만 해시)를 만족하도록 만든다.
- **Rationale**: 헌법 II(공통 ERD 수정 금지, Flyway로만 변경), 지시사항 "51·52 정의를 그대로".
- **팀 결정 대기 항목과 영향**: 52 A-3(`SUSPENDED` 삭제) → R-9로 영향 최소화. 52 C-1(`profile_image_url`) → 이 기능은 사진 연결을 003에 넘기므로 영향 없음. 51 머리말의 "신고·동의·정지 분리(팀 합의 대기)" → 동의 분리가 뒤집히면 `AgreementRecorder` 구현 한 곳만 바뀐다.

## R-15. 테스트 전략 (헌법 VI)

- **Decision**: JUnit 5 + Spring Boot Test + Spring Security Test(MockMvc, `csrf()`, `oauth2Login()`) + Testcontainers(PostgreSQL 18 이미지, Redis, Mailpit). V1 기준선 마이그레이션을 Flyway로 적용한 실제 DB에서 통합 테스트. OAuth 공급자 응답은 사용자 서비스 단위에서 고정 응답으로 시험하고, 실제 Google·GitHub 연동은 quickstart의 수동 확인으로 둔다. 시간 의존(24h·30m·15m·10m)은 `Clock` 주입과 Redis TTL 조회로 검증한다.
- **Rationale**: 헌법 VI(H2 금지, 권한 기능 통합 테스트 필수), 수용 시나리오를 그대로 테스트로 옮긴다.
- **Alternatives considered**: GreenMail 내장 메일 서버 — 가볍지만 개발 환경(Mailpit)과 달라진다.

## R-16. 빌드·구조

- **Decision**: 단일 Gradle 프로젝트(저장소 루트), 패키지는 02 §3의 `com.team.blog` 모듈 구조. 빌드 도구는 문서에 명시가 없어 Gradle을 기본으로 하되 팀 확인 대상으로 남긴다.
- **Rationale**: 헌법 I(하나의 배포 단위, 모듈러 모놀리스).
- **Alternatives considered**: Maven — 동작상 차이 없음. 팀이 정하면 따른다.

## R-17. 002(블로그 주소·닉네임)와 003(프로필)에 대한 의존

- **Decision**: 가입은 `account.application`의 `HandleService`(08 규칙: 이메일 앞부분 정리, `go-`/`gi-` 접두어, `_2`… 붙이기, 한 번 수정 검사)와 `NicknamePolicy`(09 규칙)를 호출한다. 두 규칙의 명세·테스트 소유는 002다. 001을 먼저 구현하면 002 명세가 나오기 전까지 08·09 원문 규칙대로 구현하고 002에서 테스트를 보강한다.
- **Rationale**: spec FR-005가 "블로그 주소·닉네임 규칙은 002 기능을 따른다"고 정했다.
- **Alternatives considered**: 001 안에 규칙을 따로 두기 — 규칙이 두 곳으로 갈라진다.

---

## 남은 확인 사항 (계획 진행을 막지 않음)

| # | 항목 | 현재 선택 | 확인 주체 |
|---|---|---|---|
| U-1 | Spring Boot 버전 | 4.1.1 (02 §2 근거) | 팀 확정 대기 |
| U-2 | 52 A-3 `SUSPENDED` 삭제 여부 | 판정은 `member_suspension` 기준이라 어느 쪽이든 동작 | 팀 회의 |
| U-3 | 운영 메일 발송 방식 | 설정만 바꾸는 SMTP 어댑터 | 환경 확인 (L-9) |
| U-4 | 이메일 앞부분 포함 검사의 최소 길이 | 3자 (설정값) | 팀 확인 권장 |
| U-5 | 재설정 링크 재요청 시 이전 링크 무효 | 무효로 함 | 원문 미정, 보수적 선택 |
| U-6 | 빌드 도구 | Gradle | 팀 확인 |
