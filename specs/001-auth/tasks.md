---

description: "001-auth 구현 작업 목록 (002 골격 위에 인증 추가)"
---

# Tasks: 로그인·로그아웃 (이메일 가입 + Google + GitHub)

**Input**: Design documents from `/specs/001-auth/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (web-routes.md, account-service.md, redis-keys.md), quickstart.md, `.specify/memory/constitution.md`, **[002-blog-address-nickname/tasks.md](../002-blog-address-nickname/tasks.md)**

**작업 ID 규칙**: 001은 `T101`부터, 002는 `T001`부터 번호를 쓴다(겹치지 않게). 이 목록에서 `T0xx`는 002의 작업을 가리킨다.

**선행 조건 (결정됨)**: **002를 먼저 만든다.** 이 목록은 프로젝트 골격을 다시 만들지 않는다. 다음은 이미 002가 만든 것으로 보고 그대로 쓰거나 확장한다.

| 002가 만든 것 | 002 작업 | 001에서의 쓰임 |
|---|---|---|
| Gradle Wrapper 9.8.0·`build.gradle.kts`(Boot 4.1.1, Java 21)·`compose.yaml`(postgres·redis·mailpit)·`application.yml` | T001~T006 | 의존성·설정 추가만 (T101~T104) |
| Flyway `V1__common_schema.sql`(51 통합 V1 전체 — `member`, `auth_identity`, `member_agreement`, `member_suspension` 포함) | T007 | 새 마이그레이션 없음 |
| `IntegrationTestBase`(Testcontainers PostgreSQL 18·Redis), `DatabaseCleaner`, `MutableClock`, `TestAuth`, `MemberFixtures` | T010~T014, T024, T030 | Mailpit 컨테이너만 추가 (T106) |
| `shared.error`: `ErrorResponse`, `NotFoundException`, `LoginRequiredException`, `RateLimitedException`, `GlobalExceptionHandler`, `templates/error/404.html`, `templates/layout/base.html`(CSRF 메타) | T016~T020 | 401·403 매핑 추가 |
| `shared.security`: `SecurityConfig`(CSRF·보안 헤더, 인가는 permitAll), `CurrentUser`, `CurrentUserProvider`(principal 이름 = memberId), `AccountGuard.requireLoggedIn` | T021~T023 | 세션·폼 로그인·OAuth2·`requireWritable` 추가 |
| `shared.web.ClientIpResolver`, `account.infra.RedisRateLimiter`(Lua 원자 카운터) | T026, T032 | 인증 메일·재설정·로그인 제한에 재사용 |
| `account.domain`: `Member`(handle 불변), `Provider`, `MemberStatus`, `Role`; `account.infra.MemberRepository` | T027~T029 | 가입 시 `Member` 생성 |
| `HandleService`, `NicknamePolicy`, `MemberUniqueViolationTranslator`(`SignupContext`, `ExistingSocialAccount`), 주소·닉네임 예외·문구, `static/js/account/handle-field.js`·`availability.js` | T040~T067 (002 US1·US2) | 가입 2종이 호출 (001 research R-17) |

**Tests**: 헌법 원칙 VI(권한·계정 상태 기능은 통합 테스트 필수, H2 금지)에 따라 테스트 작업을 넣는다. 각 스토리의 테스트는 구현보다 먼저 쓰고 실패를 확인한다. 통합 테스트는 Testcontainers PostgreSQL 18 + Redis + Mailpit으로 돌린다.

**Organization**: 사용자 스토리별로 묶어 각 스토리를 따로 구현·검증할 수 있게 한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: 이 작업이 속한 사용자 스토리 (US1~US4)
- 모든 경로는 저장소 루트 기준 (단일 Gradle 프로젝트, 패키지 `com.team.blog`)

---

## Phase 1: Setup (인증에 새로 필요한 의존성·설정만)

**Purpose**: 002 골격 위에 Spring Session·OAuth2 Client·메일 의존성과 설정을 더한다. **002 T001~T039 완료가 전제**다.

- [X] T101 `build.gradle.kts`에 의존성 추가: Spring Security OAuth2 Client, Spring Session Data Redis, Spring Boot Mail(JavaMailSender). Boot 4 스타터 좌표는 4.1.1 BOM으로 확인(research R-2). 기존 002 의존성은 건드리지 않는다
- [X] T102 `src/main/resources/application.yml`에 추가: `spring.session.redis.repository-type=indexed`, `spring.session.timeout=${blog.auth.session-timeout}`; 세션 쿠키 `server.servlet.session.cookie.http-only=true`, `secure=true`, `same-site=lax`, `max-age=14d`(research R-3); OAuth2 등록 `google`(scope `openid,email,profile`, `client-id=${GOOGLE_CLIENT_ID}`, `client-secret=${GOOGLE_CLIENT_SECRET}`), `github`(scope `read:user,user:email`, `${GITHUB_CLIENT_ID}`/`${GITHUB_CLIENT_SECRET}`); `blog.auth.*` 기본값(T105 키와 같음). 비밀값은 환경 변수로만(헌법 IV)
- [X] T103 [P] 개발 프로필 `src/main/resources/application-dev.yml`: 메일 **Mailpit** `spring.mail.host=${MAIL_HOST:localhost}`, `port=1025`, 인증 없음, `blog.auth.mail.from=no-reply@localhost`
- [X] T104 [P] 운영 프로필 `src/main/resources/application-prod.yml`: 메일 **Gmail SMTP** `spring.mail.host=smtp.gmail.com`, `port=587`, `spring.mail.username=${MAIL_USERNAME}`, `spring.mail.password=${MAIL_PASSWORD}`(Google 앱 비밀번호), `spring.mail.properties.mail.smtp.auth=true`, `mail.smtp.starttls.enable=true`, `mail.smtp.starttls.required=true`, `blog.auth.mail.from=${MAIL_USERNAME}`(보내는 주소 고정 — research R-13)
- [X] T105 [P] `src/main/java/com/team/blog/account/application/AuthProperties.java`(`@ConfigurationProperties("blog.auth")`): `verify-token-ttl`(24h), `reset-token-ttl`(30m), `verify-resend.per-minute`(1)·`per-day`(10), `reset-request.email-per-minute`(1)·`email-per-day`(10)·`ip-per-hour`(20), `login.max-failures`(5)·`lock-duration`(15m)·`ip-per-minute`(20), `session-timeout`(14d), `pending-social-ttl`(10m), `password.local-part-min-length`(3), `password.bcrypt-strength`(10), `mail.from` — 수치는 모두 설정값(헌법 II)
- [X] T106 `src/test/java/com/team/blog/support/IntegrationTestBase.java`(002 T010)에 Mailpit 컨테이너(`GenericContainer("axllent/mailpit")`, SMTP 1025·HTTP 8025)를 추가하고 `spring.mail.host/port`를 연결, 테스트마다 Mailpit 메시지 삭제
- [X] T107 [P] 테스트 도우미 `src/test/java/com/team/blog/support/MailpitClient.java`(Mailpit HTTP API로 수신 메일 목록·본문 조회, 링크 토큰 추출)
- [X] T108 [P] 흔한 비밀번호 목록 `src/main/resources/security/common-passwords.txt`(`Password1!`, `Qwer1234!` 등, 한 줄 한 단어, 대소문자 무시 비교용)
- [X] T109 [P] 테스트 프로필 `src/test/resources/application-test.yml`(002 T013)에 OAuth2 더미 등록값(`client-id=test` 등)과 `blog.auth.*` 테스트용 값 추가(필요 시 짧은 TTL)

**Checkpoint**: `./gradlew build -x test` 성공, 컨텍스트가 Spring Session(인덱스 저장소)·OAuth2 등록·메일 설정으로 기동된다.

---

## Phase 2: Foundational (인증 스토리 공용 기반)

**Purpose**: 세션·계정 엔터티·토큰 저장소·메일 발송·로그인 세션 확립 — US1~US4가 모두 쓴다.

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 001의 어떤 사용자 스토리도 시작하지 않는다. 또한 US1·US3(가입)은 **002 US1·US2(T040~T067) 완료**가 추가 전제다.

- [ ] T110 `src/main/java/com/team/blog/shared/security/SessionConfig.java`(Spring Session Redis 인덱스 저장소 `RedisIndexedSessionRepository`, 최대 비활성 = `blog.auth.session-timeout`, principal 이름 인덱스 = memberId) 와 `src/main/java/com/team/blog/shared/security/SecurityConfig.java`(002 T021)에 세션 규칙 추가: `sessionManagement().sessionFixation().changeSessionId()`, SecurityContext를 세션에 명시 저장(`HttpSessionSecurityContextRepository`)
- [ ] T111 [P] `src/main/java/com/team/blog/account/domain/AuthIdentity.java` JPA 엔터티(51 9개 컬럼): `member_id bigint NOT NULL`(FK RESTRICT, `uq_auth_identity_member` — 계정 하나 = 수단 하나), `provider varchar(20) NOT NULL`(`ck_auth_provider`: `LOCAL`/`GITHUB`/`GOOGLE`), `provider_user_id varchar(255) NOT NULL`(`uq_auth_identity (provider, provider_user_id)`; LOCAL = 소문자·trim 이메일, GOOGLE = `sub`, GITHUB = 숫자 ID 문자열), `email varchar(255) NULL`(LOCAL 필수·소문자 — `ck_auth_local_email`: `provider <> 'LOCAL' OR (email IS NOT NULL AND email = lower(email) AND provider_user_id = email)`), `password_hash varchar(100) NULL`(`ck_auth_password`: `(provider = 'LOCAL') = (password_hash IS NOT NULL)`, `{bcrypt}` 접두), `email_verified_at timestamptz NULL`(NULL = 인증 전), `created_at`, `last_login_at timestamptz NULL`. 팩토리 `local(...)`, `social(...)`, 메서드 `markVerified(Instant)`, `changePasswordHash(String)`, `recordLogin(Instant)`
- [ ] T112 [P] `src/main/java/com/team/blog/account/domain/MemberAgreement.java`(복합 PK `member_id`+`type`, `type varchar(20)` — `ck_member_agreement_type`: `TERMS`/`PRIVACY`/`AI`, `agreed_at timestamptz NOT NULL`) 와 `src/main/java/com/team/blog/account/domain/AgreementType.java`
- [ ] T113 [P] `src/main/java/com/team/blog/account/domain/MemberSuspension.java`(읽기 + 자동 해제만: `reason varchar(200) NOT NULL`, `started_at`, `ends_at timestamptz NULL`(NULL = 영구), `suspended_by bigint NOT NULL`, `lifted_at`, `lifted_by bigint NULL` — `ck_member_suspension_lift`: `lifted_by IS NULL OR lifted_at IS NOT NULL`; 메서드 `liftAutomatically(Instant)` = `lifted_at = now`, `lifted_by = NULL`)
- [ ] T114 [P] 저장소 `src/main/java/com/team/blog/account/infra/AuthIdentityRepository.java`(`findByProviderAndProviderUserId`, `findLocalByEmail`, `findByEmailAndProviderNot`(FR-033·재설정 안내용, 탈퇴 유예·익명 처리 회원 제외), `findByMemberId`), `src/main/java/com/team/blog/account/infra/MemberAgreementRepository.java`, `src/main/java/com/team/blog/account/infra/MemberSuspensionRepository.java`(`findCurrent(memberId, now)` = `lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now)`, `ix_member_suspension_member` 사용)
- [ ] T115 [P] 통합 테스트 `src/test/java/com/team/blog/account/integration/RedisTokenStoreIT.java`(발급 토큰은 32바이트 Base64 URL-safe, Redis 키는 `auth:verify:{sha256}`·`auth:reset:{sha256}`로 원문 없음, `GETDEL`로 한 번만 소비, 새 발급 시 `auth:verify-current:{memberId}`·`auth:reset-current:{memberId}` 포인터로 이전 토큰 삭제, TTL 24h/30m)
- [ ] T116 `src/main/java/com/team/blog/account/infra/RedisTokenStore.java`(`issue(TokenKind, memberId)` → 원문 토큰, `consume(TokenKind, rawToken)` → `Optional<memberId>`, `peek`(재설정 화면 표시용, 소비 안 함); `SecureRandom` 32바이트, SHA-256 키 — contracts/redis-keys.md)
- [ ] T117 [P] 메일 포트·어댑터: `src/main/java/com/team/blog/account/application/MailSender.java`(포트) 와 `src/main/java/com/team/blog/account/infra/SmtpMailSender.java`(`JavaMailSender` + Thymeleaf 메일 템플릿 렌더링, From = `blog.auth.mail.from`, 실패 시 예외를 삼키고 수신 주소·토큰을 마스킹한 로그만 — 헌법 V)
- [ ] T118 [P] `src/main/java/com/team/blog/shared/security/MemberPrincipal.java`(principal 이름 = memberId 문자열, 권한 `ROLE_USER`/`ROLE_ADMIN`) 와 `src/main/java/com/team/blog/shared/security/LoginSessionEstablisher.java`(`establish(memberId, request, response)`: 세션 ID 새로 발급 + SecurityContext 저장 — 가입 직후 로그인·소셜 가입 완료에서 사용, FR-028)
- [ ] T119 [P] 이벤트 기반 `src/main/java/com/team/blog/shared/event/DomainEvent.java`(마커) — 토큰 필드를 가진 이벤트는 `toString()`에서 마스킹(FR-014)

**Checkpoint**: T115 통과, 기존 002 테스트 전부 통과(세션 저장소 추가 후에도 `TestAuth` 기반 테스트 유지).

---

## Phase 3: User Story 1 - 이메일로 가입하고 인증한 뒤 글을 쓸 수 있다 (Priority: P1) 🎯 MVP

**Goal**: 이메일·블로그 주소·비밀번호·닉네임·약관 2개로 가입하면 미인증 계정 + 로그인 + 커밋 후 인증 메일, 링크로 인증하면 쓰기가 허용되고, 인증 전 쓰기는 403 `EMAIL_NOT_VERIFIED`로 막힌다.

**Independent Test**: 새 이메일로 가입 → 인증 전 쓰기 요청(테스트 전용 쓰기 엔드포인트) 거부 → Mailpit의 인증 링크 클릭 → 쓰기 요청 허용.

### Tests for User Story 1 ⚠️ (먼저 작성, 실패 확인)

- [ ] T120 [P] [US1] 단위 테스트 `src/test/java/com/team/blog/account/unit/PasswordPolicyTest.java`: `Ab1!`(짧음), 17자, 영문 없음, 숫자 없음, 특수문자 없음, 공백 포함, 한글 포함, 이메일 앞부분 포함(앞부분 3자 이상일 때만, 대소문자 무시), `Password1!`(흔한 비밀번호) → 각각 위반; 허용 특수문자는 07 §4 목록만; 위반 목록에 "최대 16자" 안내 키 포함(SC-003)
- [ ] T121 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/EmailSignupIT.java`: `POST /signup`(CSRF) 성공 → `auth_identity`(`provider='LOCAL'`, `provider_user_id = email = 소문자·trim`, `email_verified_at IS NULL`, `{bcrypt}` 해시), `member_agreement` `TERMS`·`PRIVACY` 2행, `member.nickname_changed_at IS NULL`, 로그인 상태(세션 ID 새로 발급), `303 /signup/verify-sent`, 커밋 후 Mailpit 인증 메일 1통; 약관 하나라도 미동의 → 400; 같은 이메일(대소문자·공백만 다름) → 400 "이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]" + 계정 수 그대로; 탈퇴 유예 계정 이메일 → 400 "탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요"; 비밀번호 위반 → 400(원문 다시 채우지 않음); 002 규칙 연동 — `handle=go-kim` → 400 `HANDLE_PREFIX_MISMATCH`, `handle=admin` → 400 + `admin_2` 제안, 닉네임 위반 → 400 해당 코드 문구; 메일 발송 실패(`MailSender` 대체 Bean이 예외) → 가입은 성공 유지
- [ ] T122 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/EmailSignupConcurrencyIT.java`: 같은 이메일 동시 가입 20건 → 계정 1개, 나머지는 "이미 가입된 이메일" 안내(SC-001); 다른 이메일·같은 `handle` 동시 20건 → 1건 성공, 나머지 409 "방금 다른 분이 이 주소를 사용했어요. `…_2`는 어떠세요?"(002 번역기 연동); 같은 닉네임(대소문자만 다름) 동시 → 1건 성공, 나머지 409 "방금 다른 분이 이 닉네임을 사용했어요"
- [ ] T123 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/EmailVerificationIT.java`: 유효 링크 `GET /auth/verify?token=` → `email_verified_at` 기록 + "인증이 완료됐어요"; 같은 링크 재사용 → "링크가 만료됐어요. [인증 메일 다시 보내기]"(SC-007); 24시간 경과(Redis TTL 만료) → 만료 안내; `POST /auth/verify/resend` 1분 안 두 번째 → 발송 없음 "잠시 후 다시 시도해 주세요", 하루 11번째 → 발송 없음, 새 메일 발송 후 이전 링크 무효(FR-009); 이미 인증됨 → "이미 인증된 계정이에요"
- [ ] T124 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/shared/security/WriteGuardIT.java` + 테스트 전용 쓰기 엔드포인트 `src/test/java/com/team/blog/support/WriteProbeController.java`(`POST /test/write`가 `AccountGuard.requireWritable` 호출 후 행 1개 생성): 미인증 → REST 403 `EMAIL_NOT_VERIFIED`·SSR 403 "이메일 인증 후 이용할 수 있어요" + [인증 메일 다시 보내기], 데이터 생성 0건(SC-002); 인증됨 → 허용; 비회원 → 401 `LOGIN_REQUIRED`/SSR `303 /login?redirect=…`; 탈퇴 유예 → 403 `ACCOUNT_WITHDRAWN`/SSR `303 /account/restore`; 인증 여부는 세션이 아니라 DB(`email_verified_at`)에서 읽어 다른 기기 인증이 즉시 반영됨

### Implementation for User Story 1

- [ ] T125 [P] [US1] `src/main/java/com/team/blog/account/domain/PasswordPolicy.java`(순수 함수 `validate(rawPassword, email)` → 위반 목록: 8~16자, 영문(대소문자 중) 1+·숫자 1+·특수문자 1+, 허용 문자 = 영문·숫자·`` ! @ # $ % ^ & * ( ) - _ = + [ ] { } ; : ' " , . < > / ? \ | ` ~ ``(공백·한글 불가), 이메일 `@` 앞부분(`local-part-min-length` 3자 이상일 때) 포함 금지, `security/common-passwords.txt` 금지 — 003 비밀번호 변경과 공유) 와 `src/main/java/com/team/blog/account/domain/PasswordPolicyViolation.java`
- [ ] T126 [P] [US1] 가입 예외 `src/main/java/com/team/blog/account/application/DuplicateEmailException.java`, `src/main/java/com/team/blog/account/application/WithdrawnAccountExistsException.java`, `src/main/java/com/team/blog/account/application/AgreementRequiredException.java`, 명령 `src/main/java/com/team/blog/account/application/EmailSignupCommand.java`(email, handle, password, passwordConfirm, nickname, agreeTerms, agreePrivacy)
- [ ] T127 [P] [US1] 이벤트 `src/main/java/com/team/blog/shared/event/MemberSignedUp.java`(memberId, provider), `src/main/java/com/team/blog/shared/event/VerificationMailRequested.java`(memberId, email, rawToken — 마스킹), `src/main/java/com/team/blog/shared/event/EmailVerified.java`(memberId)
- [ ] T128 [US1] `src/main/java/com/team/blog/shared/security/AccountGuard.java`(002 T023)에 `requireWritable(Optional<CurrentUser>)` 추가(42 §3 순서: 비회원 → `LoginRequiredException`, 탈퇴 유예 → `AccountStatusException(ACCOUNT_WITHDRAWN)`, 인증 전 → `AccountStatusException(EMAIL_NOT_VERIFIED)`; PK 조회 1회), `src/main/java/com/team/blog/shared/error/AccountStatusException.java`, `GlobalExceptionHandler`에 403 매핑(REST `{code}`; SSR `EMAIL_NOT_VERIFIED` → `templates/auth/forbidden-unverified.html` + 재발송 버튼, `ACCOUNT_WITHDRAWN` → `303 /account/restore`) 와 템플릿 `src/main/resources/templates/auth/forbidden-unverified.html`
- [ ] T129 [US1] `src/main/java/com/team/blog/account/application/AgreementRecorder.java`(`TERMS`·`PRIVACY` 두 행을 같은 트랜잭션에서 생성, 하나라도 미동의면 계정 생성 전 `AgreementRequiredException` — 51 §4: DB 제약 없음, Service 책임)
- [ ] T130 [US1] `src/main/java/com/team/blog/account/application/EmailSignupService.java`: 바깥 메서드(트랜잭션 없음)가 안쪽 `@Transactional` 메서드를 호출 — 안쪽: 약관 확인 → 이메일 정규화(trim·소문자, 형식, 254자 이하) → `PasswordPolicy`·비밀번호 확인 일치 → LOCAL 중복 확인(탈퇴 유예면 `WithdrawnAccountExistsException`, 아니면 `DuplicateEmailException`) → `HandleService.validateForSignup(handle, LOCAL)` → `NicknamePolicy.validate(nickname, null)` → `new Member(...)` + `AuthIdentity.local(...)`(BCrypt 10) + `AgreementRecorder` → `saveAndFlush` → `RedisTokenStore.issue(VERIFY)` → `VerificationMailRequested`·`MemberSignedUp` 발행. 바깥: `DataIntegrityViolationException` 중 `uq_auth_identity` → `DuplicateEmailException`, 그 밖은 `MemberUniqueViolationTranslator.translate(e, new SignupContext(LOCAL, null))`(트랜잭션 경계 밖 — 002 contract §6)
- [ ] T131 [US1] `src/main/java/com/team/blog/account/application/EmailVerificationService.java`: `verify(token)` → `VERIFIED`(토큰 `GETDEL` → `markVerified(now)` → `EmailVerified` 발행) / `EXPIRED_OR_USED`; `resend(memberId)` → `SENT`/`RATE_LIMITED`/`ALREADY_VERIFIED`(키 `auth:verify-resend:{memberId}:min` 1회/1분, `auth:verify-resend:{memberId}:{yyyyMMdd}` 10회/1일 — `RedisRateLimiter`, 새 토큰 발급 시 이전 토큰 삭제)
- [ ] T132 [US1] `src/main/java/com/team/blog/account/application/VerificationMailListener.java`(`@TransactionalEventListener(phase = AFTER_COMMIT)`로 `VerificationMailRequested` 처리 → `MailSender`, 링크 `/auth/verify?token=…`) 와 메일 템플릿 `src/main/resources/templates/mail/verify.html`(24시간 유효 안내)
- [ ] T133 [US1] `src/main/java/com/team/blog/account/web/AuthController.java`(가입·인증 부분): `GET /signup`, `POST /signup`(폼 `email`, `handle`, `password`, `passwordConfirm`, `nickname`, `agreeTerms`, `agreePrivacy`; 002 예외 `HandleViolationException`·`HandleTakenException`·`NicknameViolationException`과 가입 예외를 받아 같은 화면 400/409 + 칸별 안내·대안 제안 버튼, 비밀번호 원문은 다시 채우지 않음; 성공 시 `LoginSessionEstablisher` → `303 /signup/verify-sent`), `GET /signup/verify-sent`(로그인 필요), `GET /auth/verify?token=`(결과 화면 200), `POST /auth/verify/resend`(로그인 필요)
- [ ] T134 [P] [US1] 템플릿 `src/main/resources/templates/auth/signup.html`(`layout/base.html` 사용; 002 web-routes §2의 블로그 주소 칸 `devlog.com/@[ … ]` + "이메일 앞부분으로 미리 채웠어요. 이메일을 드러내고 싶지 않으면 바꿔 주세요." + "⚠ 블로그 주소는 가입 후 바꿀 수 없어요.", 칸 속성 `inputmode="latin"`·`autocapitalize="off"`·`lang="en"`, `static/js/account/handle-field.js`·`availability.js` 연결; 닉네임 칸은 빈칸 + 사용 가능 여부 표시; 비밀번호 규칙별 ✓ 글자 표시와 "최대 16자"; 약관 2개), `src/main/resources/templates/auth/verify-sent.html`, `src/main/resources/templates/auth/verify-result.html`
- [ ] T135 [P] [US1] `src/main/resources/static/js/auth/password-rules.js`(규칙별 충족 여부를 글자와 ✓ 기호로 표시, 색만으로 구분하지 않음 — FR-015. 서버 `PasswordPolicy`가 최종 판정)
- [ ] T136 [US1] `src/main/java/com/team/blog/shared/security/SecurityConfig.java`에 경로 규칙 추가: `/signup`, `/auth/verify`, `/api/handles/**`, `/api/nicknames/**`, 정적 자원 공개; `/signup/verify-sent`, `/auth/verify/resend`는 인증 필요(비회원은 로그인 화면으로)

**Checkpoint**: T120~T124 통과. 이메일 가입·인증·쓰기 차단이 단독으로 동작한다.

---

## Phase 4: User Story 2 - 이메일로 로그인·로그아웃한다 (Priority: P1)

**Goal**: 이메일·비밀번호 로그인(14일 유지, 세션 고정 방지, 상대 경로만 이동), 동일 실패 문구, 5회 실패 15분 잠금·IP 1분 20회, 정지·탈퇴 유예 처리, 로그아웃 시 서버 세션과 브라우저 임시 데이터 삭제.

**Independent Test**: 로그인 → 보던 페이지로 이동 → 로그아웃 → 쓰기 요청 거부, 틀린 비밀번호 5회 → 잠금.

### Tests for User Story 2 ⚠️ (먼저 작성, 실패 확인)

- [ ] T137 [P] [US2] 단위 테스트 `src/test/java/com/team/blog/shared/security/RedirectTargetValidatorTest.java`(`/manage/posts` 허용; `https://evil.example`, `//evil.example`, `/\evil`, 제어 문자 포함, 스킴·호스트 포함 → `/`)
- [ ] T138 [P] [US2] 통합 테스트 `src/test/java/com/team/blog/account/integration/LoginIT.java`: 성공 → `last_login_at` 기록, 저장된 요청 또는 검증된 `redirect`로 `303`, 로그인 전후 세션 ID 다름(세션 고정 방지); 없는 이메일·틀린 비밀번호 응답 문구가 글자 하나까지 같음 "이메일 또는 비밀번호가 올바르지 않아요"(SC-006); 같은 계정 5회 연속 실패 → 올바른 비밀번호도 "잠시 후 다시 시도해 주세요(약 15분)", `auth:login-lock:*` TTL ≈ 900초, 만료 후 성공(SC-005); 없는 이메일도 같은 잠금; 같은 IP 21번째 시도 → 같은 잠금 문구; 키에 원문 이메일 없음(`sha256`)
- [ ] T139 [P] [US2] 통합 테스트 `src/test/java/com/team/blog/account/integration/AccountStatusOnLoginIT.java`: 진행 중 정지(`member_suspension`) + 올바른 비밀번호 → 로그인 거부 "정지된 계정이에요 (~기한, 사유)"(`ACCOUNT_SUSPENDED`), 틀린 비밀번호면 일반 실패 문구; `ends_at`이 지난 정지만 있음 → 성공 + `lifted_at` 기록(`status='SUSPENDED'`였으면 `ACTIVE`로 되돌림 — research R-9); 탈퇴 유예 → 복구 전용 세션, `/account/restore`·`/logout`·정적 자원 외 모든 요청이 `303 /account/restore`(42 P-12)
- [ ] T140 [P] [US2] 통합 테스트 `src/test/java/com/team/blog/account/integration/LogoutAndSessionIT.java`: CSRF 없는 `POST /logout` → 거부; CSRF 포함 → Redis에서 그 세션 삭제, 쿠키 삭제, `303 /`, 홈 화면에 1회용 정리 플래시(memberId) 존재; 로그아웃 후 쓰기 요청 → 401/로그인 화면(FR-032); 세션 쿠키 `HttpOnly; Secure; SameSite=Lax`, Max-Age 14일; 세션 최대 비활성 14일, Spring Session principal 인덱스로 memberId 세션 조회 가능; 하루가 지난 세션의 요청에서 쿠키를 다시 내려 줌(`SessionCookieRefreshFilter`)

### Implementation for User Story 2

- [ ] T141 [P] [US2] `src/main/java/com/team/blog/shared/security/RedirectTargetValidator.java`(`/`로 시작, `//`·`/\`로 시작하지 않음, 스킴·호스트·제어 문자 없음, 실패 시 `/` — research R-12)
- [ ] T142 [US2] `src/main/java/com/team/blog/account/infra/LocalUserDetailsService.java`(정규화 이메일로 LOCAL `AuthIdentity` 조회 → `MemberPrincipal`(이름 = memberId); 없는 이메일은 `DaoAuthenticationProvider` 더미 해시 비교로 시간 차이 축소, `DelegatingPasswordEncoder` BCrypt 10)
- [ ] T143 [US2] `src/main/java/com/team/blog/account/application/LoginAttemptService.java`: `checkAllowed(emailNorm, ip)`(`auth:login-lock:{sha256(email)}` 존재 → `LoginLockedException`, `auth:login-ip:{ip}` 20회/1분 초과 → `IpRateLimitedException`), `recordFailure(emailNorm)`(`auth:login-fail:{sha256(email)}` +1, TTL 15m 연장, 5회째 잠금 키 15m), `recordSuccess(emailNorm)`(실패 카운터 삭제)
- [ ] T144 [US2] `src/main/java/com/team/blog/account/application/AccountStatusChecker.java`: `checkOnLogin(memberId)` → `ACTIVE` / `SUSPENDED(endsAt, reason)` / `WITHDRAWN_PENDING`(`status = 'WITHDRAWN' AND deleted_at IS NULL`); 정지 판정은 `member_suspension`의 `lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now)`, 기간 지난 정지는 쓰기 트랜잭션에서 자동 해제(research R-9). 폼·소셜 로그인 공용
- [ ] T145 [US2] 로그인 처리기 `src/main/java/com/team/blog/shared/security/LoginAttemptFilter.java`(인증 전 `checkAllowed`), `src/main/java/com/team/blog/shared/security/FormLoginSuccessHandler.java`(비밀번호 일치 후 `AccountStatusChecker` — 정지면 로그인 취소·`/login?suspended` 안내, 탈퇴 유예면 복구 전용 세션 표시 후 `303 /account/restore`; 정상이면 `recordSuccess`, `AuthIdentity.recordLogin(now)`, `RedirectTargetValidator` 통과한 대상으로 `303`), `src/main/java/com/team/blog/shared/security/FormLoginFailureHandler.java`(`recordFailure`, 항상 같은 실패 문구)
- [ ] T146 [US2] 복구 전용 세션 `src/main/java/com/team/blog/shared/security/RestoreOnlySessionFilter.java`(복구 전용 표시가 있으면 `/account/restore`, `/logout`, 정적 자원 외 → `303 /account/restore`) 와 `src/main/java/com/team/blog/account/web/AccountRestoreEntryController.java`(`GET /account/restore` 화면만 — 복구 동작 `POST /account/restore`는 13·44 소유) + `src/main/resources/templates/auth/restore.html`([복구하기] [로그아웃])
- [ ] T147 [P] [US2] `src/main/java/com/team/blog/shared/security/SessionCookieRefreshFilter.java`(하루 한 번 세션 쿠키를 Max-Age 14일로 다시 내려 마지막 활동 기준 유지 — research R-3)
- [ ] T148 [US2] `src/main/java/com/team/blog/shared/security/SecurityConfig.java`에 폼 로그인(`loginPage("/login")`, 사용자 이름 파라미터 `email`, T145 처리기·필터 연결, T146·T147 필터 등록)과 로그아웃(`POST /logout`, 세션 무효화·쿠키 삭제, `303 /`, 1회용 플래시로 memberId 전달) 추가; 레이아웃 `src/main/resources/templates/layout/base.html`(002 T020)에 로그아웃 버튼(CSRF 포함)과 홈 1회 정리 훅 추가; `src/main/resources/static/js/auth/auth-logout.js`(제출 전 IndexedDB `draft:{memberId}:*`, `draft-backup:{memberId}:*`만 삭제, 홈에서 플래시 값으로 한 번 더 실행 — research R-11)
- [ ] T149 [US2] `src/main/java/com/team/blog/account/web/AuthController.java`에 `GET /login`(`redirect` 선택 값, `?error` "이메일 또는 비밀번호가 올바르지 않아요", 잠금·IP 초과 "잠시 후 다시 시도해 주세요(약 15분)", `?suspended` "정지된 계정이에요 (~기한, 사유)", `?error=social` 문구) 추가 + 템플릿 `src/main/resources/templates/auth/login.html`([비밀번호 찾기] 링크, 소셜 버튼 자리는 US3 T164에서 채움)

**Checkpoint**: US1 + US2로 이메일 가입·로그인·로그아웃이 완결된다.

---

## Phase 5: User Story 3 - Google·GitHub 계정으로 가입·로그인한다 (Priority: P1)

**Goal**: 소셜 첫 로그인은 계정 없이 가입 마무리 화면(닉네임 미리 채움, `go-`/`gi-` 고정 접두어 주소, 약관, 같은 이메일 다른 수단 안내)으로 가고, 완료하면 계정이 생기며, 연결된 계정은 바로 로그인된다.

**Independent Test**: 같은 Google 계정으로 두 번 로그인해 계정이 1개임을, 같은 이메일의 LOCAL 계정과 Google 계정이 서로 다른 계정임을 확인한다.

### Tests for User Story 3 ⚠️ (먼저 작성, 실패 확인)

- [ ] T150 [P] [US3] 단위 테스트 `src/test/java/com/team/blog/account/unit/GoogleOidcUserServiceTest.java`(고정 OIDC 응답: `sub`로 식별, `email_verified = true`일 때만 이메일 사용, `name`을 표시 이름으로)
- [ ] T151 [P] [US3] 단위 테스트 `src/test/java/com/team/blog/account/unit/GitHubOAuth2UserServiceTest.java`(고정 응답: 숫자 `id`로 식별 — 로그인 이름 변경과 무관, `/user/emails`의 `primary && verified`만 사용, `name`이 비면 `login`을 표시 이름으로 — 002 research R-13)
- [ ] T152 [P] [US3] 통합 테스트 `src/test/java/com/team/blog/account/integration/SocialLoginIT.java`(Spring Security Test `oauth2Login()`/성공 처리기 직접 호출): 처음 로그인 → 계정 없음, 세션에 `PENDING_SOCIAL_SIGNUP`, `303 /signup/social`; 연결된 계정 → 로그인(세션 ID 새로 발급), 그 계정의 이메일이 바뀌어도 같은 계정; 정지·탈퇴 유예 처리는 폼 로그인과 같음; `state` 불일치·오류 → `/login?error=social` "소셜 로그인에 실패했어요. 다시 시도해 주세요"
- [ ] T153 [P] [US3] 통합 테스트 `src/test/java/com/team/blog/account/integration/SocialSignupIT.java`: `GET /signup/social` — 닉네임은 `NicknamePolicy.suggestFromSocialName`(`Kim Min-seo`→`KimMinseo`, `A`→빈칸 + "닉네임을 입력해 주세요"), 주소는 `go-` 고정 + `HandleService.prefill(인증 이메일, GOOGLE)`의 본문; `POST /signup/social` 완료 → `auth_identity.provider='GOOGLE'`, `provider_user_id = sub`, `email_verified_at = member.created_at`, 약관 2행, 로그인, 대기 정보 삭제, `303 /`; 본문에 `gi-kim`(Google) → 400 `HANDLE_PREFIX_MISMATCH`; 같은 이메일의 LOCAL 계정이 있으면 "이 이메일로 가입한 계정이 이미 있어요. [기존 계정으로 로그인] [새 계정 만들기]"(FR-033), 인증되지 않은 이메일·탈퇴 회원이면 안내 없음; `POST /signup/social/cancel` → 대기 정보 삭제, `303 /login`, 계정 수 그대로; [새 계정 만들기]로 완료 → 별도 계정; 10분 경과(`MutableClock`) 후 완료·화면 → `303 /login` "다시 소셜 로그인해 주세요"; 인증 이메일 없는 GitHub → 이메일 입력칸, 완료 후 미인증 + Mailpit 인증 메일
- [ ] T154 [P] [US3] 통합 테스트 `src/test/java/com/team/blog/account/integration/SocialSignupConcurrencyIT.java`: 같은 대기 정보로 마무리 20건 동시 → 계정 1개(SC-001), 나머지는 이미 생긴 계정으로 로그인 — `uq_auth_identity`가 걸린 경우와 `uq_member_handle`이 먼저 걸린 경우 모두(002 research R-7 `ExistingSocialAccount`)

### Implementation for User Story 3

- [ ] T155 [P] [US3] `src/main/java/com/team/blog/account/domain/SocialProfile.java`(provider, providerUserId, verifiedEmail?, displayName, pictureUrl) 와 `src/main/java/com/team/blog/account/domain/PendingSocialSignup.java`(같은 필드 + `createdAt`, `isExpired(now, pending-social-ttl 10m)`, 세션 속성 `PENDING_SOCIAL_SIGNUP`; 사진 주소는 화면 전달용, DB 저장 안 함)
- [ ] T156 [P] [US3] `src/main/java/com/team/blog/account/infra/GoogleOidcUserService.java`(OIDC `sub`·`email`·`email_verified`·`name`·`picture` → `SocialProfile`)
- [ ] T157 [P] [US3] `src/main/java/com/team/blog/account/infra/GitHubOAuth2UserService.java`(숫자 `id`, `/user/emails`에서 `primary && verified`, 표시 이름 `name ?: login`)
- [ ] T158 [US3] `src/main/java/com/team/blog/account/application/SocialLoginService.java`: `resolve(SocialProfile)` → `ExistingMember(memberId)` / `NewSignupRequired(PendingSocialSignup)`; `findSameEmailAccounts(pending)` → 다른 수단 목록(provider만, 인증된 이메일 없으면 빈 목록, 탈퇴 유예·익명 처리 계정 제외)
- [ ] T159 [US3] `src/main/java/com/team/blog/shared/security/SocialLoginSuccessHandler.java`(기존 계정 → `AccountStatusChecker` + `LoginSessionEstablisher` + `recordLogin`; 새 계정 → 로그인시키지 않고 대기 정보 저장 후 `303 /signup/social`) 와 `src/main/java/com/team/blog/shared/security/SocialLoginFailureHandler.java`(`/login?error=social`)
- [ ] T160 [US3] `src/main/java/com/team/blog/account/application/SocialSignupService.java`: `complete(PendingSocialSignup, SocialSignupCommand)` — 바깥 메서드(트랜잭션 없음): 10분 경과면 `PendingExpiredException`; 안쪽 `@Transactional`: 약관 확인 → `HandleService.validateForSignup(handle 본문, provider)`(접두어는 서버가 부착) → `NicknamePolicy.validate(nickname, null)` → GitHub 입력 이메일이면 정규화 → `Member` + `AuthIdentity.social(...)`(인증 이메일이면 `email_verified_at = 가입 시각`, 입력 이메일이면 NULL + 인증 토큰·`VerificationMailRequested`) + 약관 2행 → `saveAndFlush`; 바깥: `DataIntegrityViolationException` → `MemberUniqueViolationTranslator.translate(e, new SignupContext(provider, providerUserId))` — `ExistingSocialAccount`면 그 계정으로 로그인(001 R-8). 명령 `src/main/java/com/team/blog/account/application/SocialSignupCommand.java`(nickname, handle, agreeTerms, agreePrivacy, useSocialPicture, email?)
- [ ] T161 [US3] `src/main/java/com/team/blog/account/application/MemberUniqueViolationTranslator.java`(002 T052)의 소셜 계정 존재 확인을 `AuthIdentityRepository.findByProviderAndProviderUserId`로 바꾸고, 002가 임시로 둔 `MemberRepository.findMemberIdByAuthIdentity` 네이티브 쿼리를 제거한다(002 `HandleConcurrencyIT`는 그대로 통과해야 함)
- [ ] T162 [US3] `src/main/java/com/team/blog/account/web/SocialSignupController.java`: `GET /signup/social`(대기 정보 없음·만료 → `303 /login` + "다시 소셜 로그인해 주세요"; 닉네임 미리 채움, 주소 본문 `HandleService.prefill`, FR-033 안내, GitHub 이메일 없음 → 이메일 칸, "프로필 사진 사용" 기본 체크), `POST /signup/social`(폼 `nickname`, `handle`(본문만), `agreeTerms`, `agreePrivacy`, `useSocialPicture`, `email`(GitHub 이메일 없을 때만); 규칙 위반 400·경합 409 같은 화면, 성공 → `LoginSessionEstablisher` → 대기 정보 삭제 → `303 /`. 사진 사용 시 003의 사진 복사 단계로 넘길 값만 세션에 남기고, 003 구현 전에는 `/`로 이동), `POST /signup/social/cancel`(대기 정보 삭제 → `303 /login`)
- [ ] T163 [P] [US3] 템플릿 `src/main/resources/templates/auth/social-signup.html`(주소 칸 `devlog.com/@go-[ 본문 ]` — 접두어는 고칠 수 없는 고정 글자, `static/js/account/handle-field.js`의 소셜 모드·`availability.js` 연결; 닉네임 칸; 약관 2개; "프로필 사진 사용"; 같은 이메일 안내와 [기존 계정으로 로그인]은 `POST /signup/social/cancel` 폼)
- [ ] T164 [US3] `src/main/java/com/team/blog/shared/security/SecurityConfig.java`에 `oauth2Login()`(로그인 화면 `/login`, `userInfoEndpoint`에 T156·T157, 성공·실패 처리기 T159) 추가, `/signup/social/**` 접근 규칙; `templates/auth/login.html`에 [Google로 계속하기]`/oauth2/authorization/google`·[GitHub로 계속하기]`/oauth2/authorization/github` 추가

**Checkpoint**: 이메일·Google·GitHub 세 수단으로 가입·로그인이 모두 동작한다.

---

## Phase 6: User Story 4 - 비밀번호를 잊었을 때 재설정한다 (Priority: P2)

**Goal**: 가입 여부를 드러내지 않는 비밀번호 찾기, 30분·1회용 재설정 링크, 새 비밀번호 저장 시 모든 기기 로그아웃.

**Independent Test**: 가입된 이메일과 없는 이메일의 화면 문구가 같음 → 재설정 링크로 비밀번호 변경 → 다른 브라우저 세션이 다음 요청에서 로그인 필요.

### Tests for User Story 4 ⚠️ (먼저 작성, 실패 확인)

- [ ] T165 [P] [US4] 통합 테스트 `src/test/java/com/team/blog/account/integration/PasswordResetIT.java`: 가입·미가입 이메일 `POST /password/forgot` 응답이 글자 하나까지 같음 "가입된 이메일이면 안내 메일을 보냈어요"(SC-006), Mailpit — LOCAL만: 재설정 링크, LOCAL + 같은 이메일 Google: 링크 + "그 계정은 Google로 로그인하세요", Google만: 링크 없는 "이 이메일은 Google로 가입되어 비밀번호가 없어요", 없음: 메일 없음; 제한 — 같은 이메일 1분 2번째·하루 11번째, 같은 IP 1시간 21번째 → 발송 없음(문구 같음); `GET /password/reset?token=` 유효 → 입력 화면(토큰 소비 안 함); 정책 위반 새 비밀번호 → 400, 토큰 그대로; 성공 → 해시 변경, `303 /login` "비밀번호를 바꿨어요. 다시 로그인해 주세요"; 같은 링크 재사용·30분 경과 → 만료 화면(SC-007); 새 링크 요청 시 이전 링크 무효; 정지 회원도 재설정 가능
- [ ] T166 [P] [US4] 통합 테스트 `src/test/java/com/team/blog/account/integration/SessionRevokerIT.java`: 같은 회원으로 세션 2개(브라우저 A·B) → A에서 재설정 완료 → B의 다음 요청이 로그인 필요(SC-004), Redis에서 그 memberId의 세션 0개; 다른 회원의 세션은 그대로(소유 범위)

### Implementation for User Story 4

- [ ] T167 [P] [US4] 이벤트 `src/main/java/com/team/blog/shared/event/PasswordResetMailRequested.java`(email, kind = `LOCAL_LINK`/`SOCIAL_ONLY`, rawToken? — 마스킹, otherProviders) 와 `src/main/java/com/team/blog/shared/event/PasswordResetCompleted.java`(memberId)
- [ ] T168 [US4] `src/main/java/com/team/blog/account/application/SessionRevoker.java`(`revokeAll(memberId)` → 삭제 세션 수; `FindByIndexNameSessionRepository`로 principal = memberId 세션 전부 삭제. 43(정지)·003(비밀번호 변경)도 사용)
- [ ] T169 [US4] `src/main/java/com/team/blog/account/application/PasswordResetService.java`: `request(email, ip)` — 항상 같은 결과; 제한 키 `auth:reset-req:email:{sha256(email)}:min`(1/1분), `…:{yyyyMMdd}`(10/1일), `auth:reset-req:ip:{ip}`(20/1시간); LOCAL 있으면 `RedisTokenStore.issue(RESET)` + 다른 수단 목록, 소셜만이면 안내만, 없으면 아무것도 안 함 → `PasswordResetMailRequested`; `reset(token, newPassword, confirm)` — `PasswordPolicy` 먼저(위반 시 토큰 소비 안 함) → `consume`(`GETDEL`) → 해시 저장 → `PasswordResetCompleted` 발행
- [ ] T170 [US4] `src/main/java/com/team/blog/account/application/PasswordResetListeners.java`(`AFTER_COMMIT`: `PasswordResetMailRequested` → 메일, `PasswordResetCompleted` → `SessionRevoker.revokeAll`) 와 메일 템플릿 `src/main/resources/templates/mail/reset.html`(30분 유효 링크 `/password/reset?token=…` + 소셜 안내), `src/main/resources/templates/mail/social-only.html`
- [ ] T171 [US4] `src/main/java/com/team/blog/account/web/PasswordResetController.java`(`GET/POST /password/forgot`, `GET /password/reset?token=`(토큰은 숨은 필드로 유지), `POST /password/reset`) 와 템플릿 `src/main/resources/templates/auth/password-forgot.html`, `src/main/resources/templates/auth/password-reset.html`(만료 시 "링크가 만료됐어요. [비밀번호 찾기]")
- [ ] T172 [US4] `src/main/java/com/team/blog/shared/security/SecurityConfig.java`에 `/password/**` 공개 규칙 추가(로그인 전 기능 — 정지·탈퇴 유예 회원도 접근, 복구 전용 세션 필터 예외 목록에도 추가)

**Checkpoint**: US1~US4가 각자 테스트로 검증된다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T173 [P] 비밀값 누출 점검 `src/test/java/com/team/blog/account/integration/SecretLeakIT.java`: 가입·인증·재설정·로그인 흐름의 로그 캡처에 비밀번호 원문·토큰 원문·원문 이메일 키가 없고, Redis 키 전체에 원문 토큰·이메일이 없음(FR-014)
- [ ] T174 [P] `src/test/java/com/team/blog/shared/security/SecurityHeadersIT.java`(002 T025)에 인증 화면(`/login`, `/signup`, `/signup/social`, `/password/forgot`) 응답 헤더와 세션 쿠키 속성 검사 추가
- [ ] T175 quickstart.md S1~S6 수행(`docker compose up -d postgres redis mailpit`, `./gradlew bootRun`, Mailpit 웹 8025) + 002 quickstart의 가입 화면 수동 항목(S1-2~4 자동 채움 중단·소문자 표시·한글 자판, S2-5 확인 후 선점, S7-2 1초 안 표시)을 함께 확인
- [ ] T176 [P] 실제 Google·GitHub 개발용 OAuth 앱(`http://localhost:8080/login/oauth2/code/{google|github}`)으로 수동 확인(quickstart S4) 결과를 PR 설명에 기록
- [ ] T177 운영 프로필 점검: `SPRING_PROFILES_ACTIVE=prod`에서 `MAIL_USERNAME`·`MAIL_PASSWORD`·OAuth 비밀값이 없으면 기동 실패하는지, Gmail SMTP(587 STARTTLS)로 실제 메일 1통 발송 확인; 전체 `./gradlew test` 통과

---

## Dependencies & Execution Order

### Phase Dependencies

- **002 Phase 1·2 (T001~T039)**: 001 전체의 선행 조건(프로젝트 골격).
- **Setup (Phase 1, T101~T109)**: 002 Foundational 완료 후.
- **Foundational (Phase 2, T110~T119)**: 001 Setup 완료 후. 001의 모든 스토리를 막는다.
- **US1 (Phase 3)**: Foundational + **002 US1·US2(T040~T067)** 완료 후.
- **US2 (Phase 4)**: Foundational 완료 후. 로그인할 계정이 필요하므로 테스트 데이터는 US1의 가입 또는 `MemberFixtures` + `AuthIdentity` 직접 생성으로 만든다. `AuthController`·`SecurityConfig`·`layout/base.html`을 US1과 함께 고치므로 같은 파일은 US1 다음에.
- **US3 (Phase 5)**: US2의 `AccountStatusChecker`(T144)·`AuthController`의 `/login` 화면(T149) 이후, 002 US1·US2 완료 후.
- **US4 (Phase 6)**: US1(`PasswordPolicy`, `RedisTokenStore` 사용 경로)과 US2(로그인 세션) 이후.
- **Polish (Phase 7)**: 원하는 스토리 완료 후.

### User Story Dependencies

- US1 (P1): 002 US1·US2에 의존, 001 내 다른 스토리 의존 없음 — MVP.
- US2 (P1): Foundational만으로 시작 가능(같은 파일 수정 순서만 US1 뒤).
- US3 (P1): US2(T144, T149)에 의존.
- US4 (P2): US1(T125)·US2에 의존.

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한다.
- 도메인·이벤트·예외 → Service → 리스너 → 컨트롤러·템플릿 → `SecurityConfig` 연결 순서.
- `SecurityConfig.java`, `AuthController.java`, `GlobalExceptionHandler.java`, `templates/layout/base.html`은 여러 스토리가 고치므로 [P] 없이 순서대로.

### Parallel Opportunities

- Setup: T103, T104, T105, T107, T108, T109 병렬(T101·T102 뒤, T106은 단독).
- Foundational: T111~T115, T117~T119 병렬, T116은 T115 뒤, T110은 단독.
- US1 테스트 T120~T124 병렬, 구현 T125~T127·T134·T135 병렬.
- US2 테스트 T137~T140 병렬, 구현 T141·T147 병렬.
- US3 테스트 T150~T154 병렬, 구현 T155~T157·T163 병렬.
- US4 테스트 T165·T166 병렬, 구현 T167 병렬.

---

## Parallel Example: User Story 1

```bash
# 테스트 먼저 (동시에)
Task: "T120 PasswordPolicyTest in src/test/java/com/team/blog/account/unit/PasswordPolicyTest.java"
Task: "T121 EmailSignupIT in src/test/java/com/team/blog/account/integration/EmailSignupIT.java"
Task: "T122 EmailSignupConcurrencyIT in src/test/java/com/team/blog/account/integration/EmailSignupConcurrencyIT.java"
Task: "T123 EmailVerificationIT in src/test/java/com/team/blog/account/integration/EmailVerificationIT.java"
Task: "T124 WriteGuardIT in src/test/java/com/team/blog/shared/security/WriteGuardIT.java"

# 서로 다른 파일 (동시에)
Task: "T125 PasswordPolicy", "T126 가입 예외·명령", "T127 이벤트 3종", "T134 가입 템플릿", "T135 password-rules.js"
```

## Parallel Example: User Story 3

```bash
Task: "T150 GoogleOidcUserServiceTest", "T151 GitHubOAuth2UserServiceTest", "T152 SocialLoginIT", "T153 SocialSignupIT", "T154 SocialSignupConcurrencyIT"
Task: "T155 SocialProfile·PendingSocialSignup", "T156 GoogleOidcUserService", "T157 GitHubOAuth2UserService", "T163 social-signup.html"
```

---

## Implementation Strategy

### MVP First

1. (002 T001~T067 완료 상태에서) Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US1 → **멈추고 검증**: 이메일 가입·인증·쓰기 차단(T120~T124). **001의 MVP 범위 = US1.**
3. 바로 US2(로그인·로그아웃)를 붙이는 것을 권장한다 — US1만으로는 가입 직후 세션 외에 다시 로그인할 수단이 없다.

### Incremental Delivery

1. Setup + Foundational → 세션·엔터티·토큰·메일 기반.
2. US1 → 이메일 가입·인증 (MVP).
3. US2 → 로그인·로그아웃·잠금·계정 상태.
4. US3 → Google·GitHub.
5. US4 → 비밀번호 재설정·모든 기기 로그아웃.

### Parallel Team Strategy

- Foundational 완료 후: 개발자 A US1 → US4, 개발자 B US2 → US3(공유 파일 `SecurityConfig`·`AuthController`는 병합 순서를 정해 충돌 방지).

---

## Notes

- 새 테이블·컬럼·마이그레이션 없음(51 V1 그대로, 약관은 `member_agreement`).
- 트랜잭션 안에서 메일·외부 HTTP를 호출하지 않는다(메일은 `AFTER_COMMIT`, 헌법 V).
- 비밀번호·토큰 원문을 저장·로그하지 않는다(FR-014). Redis 키에는 해시만.
- 가입 실패 문구는 가입 여부와 무관하게 같게(SC-006).
- 작업 또는 논리 묶음마다 커밋한다.
