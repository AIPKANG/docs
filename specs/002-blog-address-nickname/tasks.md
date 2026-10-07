---

description: "002-blog-address-nickname 구현 작업 목록 (프로젝트 골격 포함)"
---

# Tasks: 블로그 주소(아이디)와 닉네임

**Input**: Design documents from `/specs/002-blog-address-nickname/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (account-identity-service.md, web-routes.md), quickstart.md, `.specify/memory/constitution.md`

**작업 ID 규칙**: 002는 `T001`부터, 001-auth는 `T101`부터 번호를 쓴다(겹치지 않게). **002를 먼저 만든다.** 애플리케이션 코드가 아직 없으므로 이 목록의 Phase 1·2가 저장소 전체의 프로젝트 골격(Gradle, Spring Boot, Docker Compose, Flyway V1 공통 스키마, Testcontainers 기반, 공용 보안·오류 기반)을 만든다. 001-auth의 [tasks.md](../001-auth/tasks.md)는 이 골격을 다시 만들지 않고, 여기서 만든 클래스를 확장한다.

**Tests**: 헌법 원칙 VI(권한·소유 검사 기능은 통합 테스트 필수, H2 금지)에 따라 테스트 작업을 넣는다. 각 사용자 스토리의 테스트는 구현보다 먼저 쓰고, 실패하는 것을 확인한 뒤 구현한다. 통합 테스트는 Testcontainers PostgreSQL 18 + Redis로 돌린다.

**Organization**: 사용자 스토리별로 묶어 각 스토리를 따로 구현·검증할 수 있게 한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: 이 작업이 속한 사용자 스토리 (US1~US4)
- 모든 경로는 저장소 루트 기준 (단일 Gradle 프로젝트, 패키지 `com.team.blog`)

## Path Conventions

- 메인 코드: `src/main/java/com/team/blog/`
- 리소스: `src/main/resources/`
- 테스트: `src/test/java/com/team/blog/`, `src/test/resources/`

---

## Phase 1: Setup (프로젝트 골격 — 저장소 전체 공용)

**Purpose**: 애플리케이션이 없는 저장소에 빌드·실행·테스트 골격을 만든다. 모든 기능(001~024)이 이 골격을 공유한다.

- [X] T001 Gradle Wrapper 9.8.0을 만든다: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`(`distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip`) — 저장소 루트
- [X] T002 `settings.gradle.kts`에 `rootProject.name = "blog"`를 적는다(단일 프로젝트, 하위 프로젝트 없음)
- [X] T003 `build.gradle.kts`(Kotlin DSL)를 만든다: 플러그인 `java`, `org.springframework.boot` 4.1.1, `io.spring.dependency-management`; `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`; `group = "com.team"`; 의존성 — Spring Web MVC, Thymeleaf, Validation, Data JPA, Flyway(+ PostgreSQL 모듈), PostgreSQL JDBC 드라이버(runtime), Spring Data Redis, Spring Security; 테스트 — `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, Testcontainers JUnit Jupiter·PostgreSQL. 정확한 Boot 4 스타터 좌표는 4.1.1 BOM으로 확인한다(001 research R-2). **Spring Session·OAuth2 Client·Mail은 넣지 않는다(001 T101에서 추가)**. `tasks.withType<Test> { useJUnitPlatform() }`
- [X] T004 [P] `compose.yaml`(저장소 루트)을 만든다: `postgres`(이미지 `postgres:18`, DB `blog`, 사용자·비밀번호는 `${DB_USERNAME:-blog}`/`${DB_PASSWORD:-blog}`, 5432, 볼륨), `redis`(`redis-server --appendonly yes --appendfsync everysec --maxmemory-policy noeviction`, 6379, 볼륨 — 헌법 기술 제약), `mailpit`(이미지 `axllent/mailpit`, SMTP 1025, 웹 8025). 비밀값은 환경 변수로만(헌법 IV)
- [X] T005 [P] 애플리케이션 진입점 `src/main/java/com/team/blog/BlogApplication.java`(`@SpringBootApplication`, `@ConfigurationPropertiesScan`)과 모듈 패키지 `account/{web,application,domain,infra}`, `shared/{security,error,web,text,config}`의 `package-info.java`를 만든다(02 §3 구조, 헌법 I)
- [X] T006 [P] `src/main/resources/application.yml` 기본 설정: `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/blog}`·`username`·`password`(환경 변수), `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.flyway.enabled=true`, `spring.data.redis.host=${REDIS_HOST:localhost}`·`port`, `server.forward-headers-strategy=native`, `spring.thymeleaf` 기본값, 메시지 소스 `messages`
- [X] T007 Flyway V1 공통 스키마 `src/main/resources/db/migration/V1__common_schema.sql`를 만든다: `docs/51-erd-unified.md` "ERD 변경 제안" 절의 ```` ```sql ```` 블록(첫 줄 `-- 팀 공통 통합 V1: 20개 테이블, 149개 컬럼, 41개 FK.`, `CREATE EXTENSION IF NOT EXISTS pg_trgm;`부터 블록 끝까지)을 **한 글자도 바꾸지 않고** 그대로 옮긴다. 모든 기능이 이 V1을 공유하므로 002가 쓰지 않는 테이블(post, image, comment, notification 등)도 전부 포함한다. 51이 가리키는 `erd/V1__common_schema.sql`·`scripts/lib/extract.py`는 저장소에 없으므로 51 본문이 원본이다(헌법 II)
- [X] T008 [P] 시계 주입 설정 `src/main/java/com/team/blog/shared/config/ClockConfig.java`(`Clock.systemUTC()` Bean — 30일 제한 등 시간 규칙은 모두 이 `Clock`을 쓴다)
- [X] T009 [P] 메시지 리소스 `src/main/resources/messages.properties`를 만든다(오류 코드 → 화면 문구 보관 위치. 각 스토리가 자기 문구를 추가)

**Checkpoint**: `./gradlew build -x test`가 성공하고 `docker compose up -d postgres redis mailpit` 후 `./gradlew bootRun`이 V1을 적용하며 기동된다.

---

## Phase 2: Foundational (모든 스토리를 막는 공용 기반)

**Purpose**: 002의 모든 스토리와 이후 001이 기대는 공용 테스트·보안·오류·회원 엔터티·요청 제한·금칙어 필터 기반

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 어떤 사용자 스토리도 시작하지 않는다.

### 테스트 기반

- [X] T010 Testcontainers 공용 기반 `src/test/java/com/team/blog/support/IntegrationTestBase.java`: `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`, 정적 싱글턴 컨테이너 `PostgreSQLContainer("postgres:18")`·Redis `GenericContainer("redis:8")`(포트 6379)를 `@ServiceConnection`(또는 `@DynamicPropertySource`)으로 연결, H2 사용 금지(헌법 VI). 테스트마다 DB·Redis를 비우는 `@AfterEach` 훅 호출. 001이 Mailpit 컨테이너를 같은 클래스에 추가한다(T106)
- [X] T011 [P] 테스트용 DB·Redis 정리 도우미 `src/test/java/com/team/blog/support/DatabaseCleaner.java`(V1의 20개 테이블을 `TRUNCATE … RESTART IDENTITY CASCADE`, Redis `FLUSHDB`)
- [X] T012 [P] 조정 가능한 시계 `src/test/java/com/team/blog/support/MutableClock.java`(테스트에서 `Clock` Bean을 대체해 30일 경과 등을 재현) 와 `src/test/java/com/team/blog/support/TestClockConfig.java`(`@TestConfiguration`, `@Primary MutableClock`)
- [X] T013 [P] 테스트 프로필 `src/test/resources/application-test.yml`: `blog.text.banned-words.location=classpath:policy/test-banned-words.txt`, `blog.text.banned-words.exceptions-location=classpath:policy/test-banned-words-exceptions.txt`
- [X] T014 [P] 테스트용 금칙어 목록 `src/test/resources/policy/test-banned-words.txt`(`시발`, `씨발`, `병신`, `shit`, `fack` — quickstart §1)와 예외 목록 `src/test/resources/policy/test-banned-words-exceptions.txt`(`시발점`, `시발역`)
- [X] T015 [P] 스키마 스모크 테스트 `src/test/java/com/team/blog/support/SchemaMigrationIT.java`: Flyway V1 적용 후 20개 테이블·`pg_trgm` 확장 존재, 제약 이름 `uq_member_handle`, `ck_member_handle`, `uq_member_nickname`, `ck_member_nickname`, `ck_member_nickname_null`, `uq_auth_identity`가 `pg_constraint`/`pg_indexes`에 있음, `ddl-auto=validate`로 컨텍스트 기동 성공

### 공용 오류 기반 (`shared.error`)

- [X] T016 [P] `src/main/java/com/team/blog/shared/error/ErrorResponse.java`(REST 본문 `{ "code": "...", "message": "..." }` + 선택 필드 `suggestion`, `nextAllowedAt` — 42 §4 이유 코드 형식)
- [X] T017 [P] `src/main/java/com/team/blog/shared/error/NotFoundException.java`(404 단일 이유 코드 `NOT_FOUND`, 세분화 금지 — 42 §4, 헌법 III)
- [X] T018 [P] `src/main/java/com/team/blog/shared/error/LoginRequiredException.java`(401 `LOGIN_REQUIRED`) 와 `src/main/java/com/team/blog/shared/error/RateLimitedException.java`(필드 `retryAfterSeconds`)
- [X] T019 `src/main/java/com/team/blog/shared/error/GlobalExceptionHandler.java`(`@ControllerAdvice`): 요청이 `/api/**`이거나 `Accept: application/json`이면 `ErrorResponse` JSON, 아니면 SSR 화면. `NotFoundException` → 404 `NOT_FOUND`/공통 화면, `LoginRequiredException` → 401 `LOGIN_REQUIRED`/`303 /login?redirect={현재 상대 경로}`(로그인 화면은 001이 만든다), `RateLimitedException` → 429 `RATE_LIMITED` + `Retry-After` 헤더(research R-14). 각 스토리가 자기 예외 매핑을 이 클래스에 추가한다
- [X] T020 [P] 공통 404 화면 `src/main/resources/templates/error/404.html`("볼 수 없는 페이지예요" — 비공개·없음·탈퇴 구분 없는 같은 화면, 42 §4)와 기본 레이아웃 `src/main/resources/templates/layout/base.html`(`<meta name="_csrf">`·`<meta name="_csrf_header">`로 CSRF 토큰을 JS에 넘김, 375px 가로 스크롤 없음)

### 공용 보안 기반 (`shared.security`) — 001이 확장한다

- [X] T021 `src/main/java/com/team/blog/shared/security/SecurityConfig.java`(Spring Security 7 람다 DSL만): CSRF 켬(세션 저장 토큰, `X-CSRF-TOKEN` 헤더 허용), 보안 헤더 — `Content-Security-Policy: default-src 'self'; script-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`(12 §8, CDN은 008에서 추가), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`; 인가는 아직 `permitAll`(폼 로그인·OAuth2·세션 규칙은 001 T110(세션)·T136(가입 경로)·T148(폼 로그인·로그아웃)·T164(OAuth2)에서 같은 파일에 추가). 업무 권한은 URL이 아니라 Service에서 검사(헌법 III)
- [X] T022 [P] `src/main/java/com/team/blog/shared/security/CurrentUser.java`(record `memberId`, `role` — SecurityContext에서만 만든다, 요청 값으로 회원 ID를 받지 않음) 와 `src/main/java/com/team/blog/shared/security/CurrentUserProvider.java`(`Optional<CurrentUser> current()`: 인증 principal 이름 = `memberId` 문자열 규칙 — 001 research R-3)
- [X] T023 [P] `src/main/java/com/team/blog/shared/security/AccountGuard.java`에 `requireLoggedIn(Optional<CurrentUser>)`만 구현(비회원 → `LoginRequiredException`). `requireWritable`은 001 T128에서 추가
- [X] T024 [P] 테스트용 로그인 도우미 `src/test/java/com/team/blog/support/TestAuth.java`(MockMvc `RequestPostProcessor`·`SecurityContext`에 principal 이름 = memberId를 넣는다 — 001 로그인 구현 전에도 "로그인한 본인" 시나리오를 시험)
- [X] T025 [P] 보안 헤더 통합 테스트 `src/test/java/com/team/blog/shared/security/SecurityHeadersIT.java`(아무 경로 응답에 CSP·`nosniff`·`Referrer-Policy`가 있고, CSRF 토큰 없는 `POST`는 403)
- [X] T026 [P] 클라이언트 IP 해석기 `src/main/java/com/team/blog/shared/web/ClientIpResolver.java`(신뢰 프록시의 `X-Forwarded-For`만 반영 — `server.forward-headers-strategy` 결과의 `request.getRemoteAddr()` 사용, 001 R-6과 같은 규칙)

### 회원 엔터티·저장소 (`account`, 001과 공유)

- [X] T027 [P] `src/main/java/com/team/blog/account/domain/Provider.java`(`LOCAL`, `GOOGLE`, `GITHUB` — `auth_identity.provider`의 `ck_auth_provider` 값과 같음), `src/main/java/com/team/blog/account/domain/MemberStatus.java`(`ACTIVE`, `SUSPENDED`, `WITHDRAWN` — `ck_member_status`), `src/main/java/com/team/blog/account/domain/Role.java`(`USER`, `ADMIN` — `ck_member_role`)
- [X] T028 `src/main/java/com/team/blog/account/domain/Member.java` JPA 엔터티(테이블 `member` 14개 컬럼을 51 그대로 매핑): `id` bigint IDENTITY; `handle varchar(39) NOT NULL` — `@Column(updatable = false)`, **setter·변경 메서드 없음, 생성자에서만 설정**(FR-011); `nickname varchar(10) NULL`(NULL은 익명 처리 후만, `ck_member_nickname_null`); `nickname_changed_at timestamptz NULL`(가입 시 NULL); `bio varchar(200) NULL`, `profile_image_id bigint NULL`, `profile_image_url varchar(500) NULL`(읽기만), `role varchar(20) NOT NULL DEFAULT 'USER'`, `status varchar(20) NOT NULL DEFAULT 'ACTIVE'`, `default_visibility varchar(20) NOT NULL DEFAULT 'PUBLIC'`, `created_at`/`updated_at timestamptz NOT NULL`, `withdrawn_at`/`deleted_at timestamptz NULL`. 가입용 생성자 `Member(Handle, Nickname, Instant now)`, 닉네임 변경 메서드는 US3(T071)에서 추가
- [X] T029 `src/main/java/com/team/blog/account/infra/MemberRepository.java`(Spring Data JPA): `existsByHandle(String)`, `findHandlesIn(Collection<String>)`(`handle IN (...)` 한 번 — research R-3), `existsByNicknameIgnoreCaseExcluding(String normalized, Long excludeMemberId)`(`lower(nickname) = lower(:n) AND (:exclude IS NULL OR id <> :exclude)` — `uq_member_nickname` 함수 인덱스 사용), `findByIdForUpdate(long)`(`@Lock(PESSIMISTIC_WRITE)`), `findActiveByHandle(String)`(`status <> 'WITHDRAWN'`). 다른 모듈은 이 저장소를 직접 쓰지 않는다(헌법 I)
- [X] T030 [P] 테스트 회원 도우미 `src/test/java/com/team/blog/support/MemberFixtures.java`(001 가입 흐름 없이 `member` 행을 만든다: 정상·탈퇴 유예(`status='WITHDRAWN'`, `withdrawn_at` 설정)·익명 처리(`nickname=NULL`, `deleted_at` 설정, `handle` 유지) — `ck_member_withdrawn`, `ck_member_deleted`, `ck_member_nickname_null`을 만족하게)

### 요청 제한 (`RedisRateLimiter`, 001과 공유)

- [X] T031 [P] 단위 테스트 `src/test/java/com/team/blog/account/unit/RedisRateLimiterKeyTest.java`(키 계산·한도 판정) 와 통합 테스트 `src/test/java/com/team/blog/account/integration/RedisRateLimiterIT.java`(첫 증가 시 TTL 설정, 한도 초과 판정, TTL ≤ 창 길이, 동시 증가 원자성)
- [X] T032 `src/main/java/com/team/blog/account/infra/RedisRateLimiter.java`: `INCR` + 첫 증가 시 `EXPIRE`를 Lua 스크립트 하나로 원자 처리, `tryAcquire(String key, int limit, Duration window)` → 허용 여부 + 남은 TTL(초). 키 원문에 이메일·토큰을 넣지 않는다(001 redis-keys 규칙). 001이 같은 클래스를 그대로 쓴다

### 설정값 (헌법 II)

- [X] T033 `src/main/java/com/team/blog/account/application/AccountIdentityProperties.java`(`@ConfigurationProperties("blog.account")`: `handle.reserved`, `handle.prefill-max-body-length`(30), `handle.suggestion-batch-size`(20), `nickname.reserved`, `nickname.change-cooldown`(30d), `availability.per-ip-per-minute`(30)) 와 `application.yml`의 `blog.account.*` 기본값 — `handle.reserved`는 FR-004의 34개(admin, administrator, root, system, official, support, help, about, terms, privacy, api, login, logout, signup, settings, me, write, search, tag, tags, notifications, manage, static, assets, images, mail, www, blog, user, users, null, undefined, devlog, teamblog), `nickname.reserved`는 FR-017의 15개(관리자, 운영자, 운영진, 운영팀, 고객센터, 공식, 매니저, 스태프, admin, administrator, official, staff, manager, system, root) — `src/main/resources/application.yml`

### 금칙어 필터 (`shared.text`) — US1(주소)·US2(닉네임)·003(소개)이 공유

- [X] T034 [P] 단위 테스트 `src/test/java/com/team/blog/shared/text/TextVariantsTest.java`(소문자 입력의 4변형: 그대로 / 숫자 제거 / `0→o·1→i·3→e·4→a·5→s·7→t` / `1→l`)
- [X] T035 [P] 단위 테스트 `src/test/java/com/team/blog/shared/text/BannedWordFilterTest.java`(테스트 목록 기준: `시1발`·`sh1t`·`병1신왕` 차단, `시발점` 허용(예외 먼저 제거, 긴 단어 우선·겹치면 왼쪽), `f4ck`→`fack` 차단, 주소 본문 `_` 제거 변형(`containsBannedInHandleBody`), 반환값이 `boolean`뿐이라 걸린 단어가 밖으로 나가지 않음, 목록 파일 없음·빈 파일이면 시작 실패)
- [X] T036 [P] `src/main/java/com/team/blog/shared/text/TextVariants.java`(`of(String lowerText)` → 4개 변형 `List<String>`)
- [X] T037 [P] `src/main/java/com/team/blog/shared/text/WordListLoader.java`(한 줄 한 단어, `#` 주석·빈 줄 무시, NFC + 소문자 정규화, 불변 집합; 파일이 없거나 비면 예외로 **시작 실패**)
- [X] T038 `src/main/java/com/team/blog/shared/text/BannedWordFilter.java`(`containsBanned(String)`, `containsBannedInHandleBody(String)`; 설정 `blog.text.banned-words.location`(기본 `classpath:policy/banned-words.txt`), `blog.text.banned-words.exceptions-location`(기본 `classpath:policy/banned-words-exceptions.txt`); 로그에는 "banned word matched"와 입력 길이만 — research R-9)
- [X] T039 [P] 운영 목록 초안 `src/main/resources/policy/banned-words.txt`, `src/main/resources/policy/banned-words-exceptions.txt`(팀 검토본으로 교체 예정 — research U-3. 시작 실패를 막기 위해 최소 항목을 넣고 파일 머리에 `# 팀 검토 전 임시 목록` 주석)

**Checkpoint**: `./gradlew test`에서 T015·T025·T031·T034·T035가 통과한다. 이후 스토리 작업을 시작할 수 있다.

---

## Phase 3: User Story 1 - 가입할 때 블로그 주소를 정한다 (Priority: P1) 🎯 MVP

**Goal**: 이메일로 블로그 주소를 미리 채우고(08 §3 10단계, `go-`/`gi-` 접두어), 가입 요청의 주소를 서버가 다시 검사하며, 동시 가입에서도 한 명만 그 주소를 갖고, 가입 후에는 어떤 경로로도 바뀌지 않는다. 001 가입 흐름이 호출할 `HandleService`·`MemberUniqueViolationTranslator`와 화면 JS를 제공한다.

**Independent Test**: 같은 이메일 앞부분으로 LOCAL·GOOGLE·GITHUB `prefill`을 차례로 실행해 주소가 겹치지 않고 08 §3 예시 12개와 같음을 확인하고, `validateForSignup`의 오류 코드·대안, 동시 20건 중 1건만 성공, `handle` 변경 경로 없음을 확인한다(가입 HTTP 경로는 001에서 다시 검증).

### Tests for User Story 1 ⚠️ (먼저 작성, 실패 확인)

- [X] T040 [P] [US1] 단위 테스트 `src/test/java/com/team/blog/account/unit/HandleRulesTest.java`: 08 §3 1~9단계 매개변수 테스트(난수원 주입으로 `483920` 고정) — `Kim.Min-Seo+blog@naver.com`→`kim_min_seo`, `_kim__min_`→`kim_min`, `12345678+octocat@…`(GITHUB)→`gi-12345678`, `김민서@…`·`ab@…`→`user_483920`(GOOGLE이면 `go-user_483920`), 30자 초과 자르기 후 끝 `_` 제거, 마지막 `@` 기준; 형식 정규식 `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$` 표(본문 3~36자, 전체 최대 39자, 처음·끝 영숫자)
- [X] T041 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/HandleSignupIT.java`(Testcontainers): 빈 DB에서 08 §3 예시 12개를 순서대로 `prefill` + 행 저장 → `kim755030`, `kim755030_2`, `go-kim755030`, `gi-kim755030`, `kim755030_3`, `gokim`, `kim_min_seo`, `kim_min`, `admin_2`, `user_483920`, `go-user_483920`, `gi-12345678`(SC-001, SC-002); `validateForSignup` — LOCAL에 `go-kim` → `HANDLE_PREFIX_MISMATCH`, GOOGLE에 `gi-kim` → `HANDLE_PREFIX_MISMATCH`, GOOGLE에 본문 `kim` → `go-kim`으로 확정, `admin` → `HANDLE_RESERVED` + 제안 `admin_2`, `kim-min`·`ab` → `HANDLE_INVALID_FORMAT`, 금칙어 본문 → `HANDLE_BANNED_WORD`(제안 없음, 예외 메시지에 단어 없음), 이미 있음 → `HANDLE_DUPLICATE` + `_n` 제안; 대문자·앞뒤 공백 입력은 정리 후 통과; 사용자가 고친 36자 본문에 번호를 붙여도 본문 36자 유지
- [X] T042 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/HandleConcurrencyIT.java`: 같은 주소로 20개 스레드가 동시에 (`TransactionTemplate` 안에서 `Member` `saveAndFlush`, 트랜잭션 **밖에서** `MemberUniqueViolationTranslator.translate`) → `member` 1행, 나머지 19건은 `HandleTakenException` + 제안 `…_2`(SC-003, FR-010)
- [X] T043 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/HandleImmutabilityIT.java`: `Member`에 `handle` setter·변경 메서드가 없음(리플렉션), 닉네임 등 다른 컬럼을 바꿔 저장해도 `handle` 그대로, JPA로 `handle`을 바꾼 엔터티를 저장해도 UPDATE 문에 포함되지 않음(`updatable = false`) (US1-7, FR-011, SC-004)
- [X] T044 [P] [US1] 통합 테스트 `src/test/java/com/team/blog/account/integration/AvailabilityApiIT.java`(주소 부분): `GET /api/handles/availability?handle=` → `{available, reason, suggestion}`(`reason` ∈ `INVALID_FORMAT`·`RESERVED`·`BANNED_WORD`·`DUPLICATE`, `suggestion`은 `RESERVED`·`DUPLICATE`일 때만); `POST /api/handles/suggestion` `{email}` + CSRF → LOCAL 기준 미리 채움, 이메일 형식 아님 → `{handle: null}`, CSRF 없음 → 403; 같은 IP 31번째 요청 → 429 `RATE_LIMITED` + `Retry-After`, 두 API가 `account:handle-check:ip:{ip}` 버킷 공유, TTL ≤ 60초; 이메일이 URL·로그에 남지 않음

### Implementation for User Story 1

- [X] T045 [P] [US1] `src/main/java/com/team/blog/account/domain/HandlePrefix.java`(`NONE("")`, `GO("go-")`, `GI("gi-")`, `of(Provider)` 1:1 대응)
- [X] T046 [P] [US1] `src/main/java/com/team/blog/account/domain/HandleViolation.java`(오류 코드 enum: `HANDLE_INVALID_FORMAT`, `HANDLE_PREFIX_MISMATCH`, `HANDLE_RESERVED`, `HANDLE_BANNED_WORD`, `HANDLE_DUPLICATE`, `HANDLE_TAKEN_CONCURRENTLY`; API용 `reason()`은 `HANDLE_` 접두를 뗀 값)
- [X] T047 [US1] `src/main/java/com/team/blog/account/domain/HandleRules.java`(순수 함수): 형식 정규식 상수 `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$`(DB `ck_member_handle`과 같은 문자열 — 설정 아님), 08 §3 1~9단계 `bodyFromEmail(email, RandomGenerator)`(마지막 `@`, `+` 뒤 버림, 소문자, `.`·`-`→`_`, 허용 외 제거, 연속 `_` 하나로·처음/끝 제거, 30자 자르기 후 끝 `_` 제거, 3자 미만이면 `user_` + 6자리(`000000`~`999999`)), 정리(trim·소문자), 접두어 해석(첫 `-` 앞이 `go`/`gi`가 아니면 형식 오류), 번호 붙이기 시 본문 36자 유지(끝 자르고 끝 `_` 제거)
- [X] T048 [P] [US1] `src/main/java/com/team/blog/account/domain/Handle.java`(값 객체 `prefix`, `body`; 생성 시 형식 검사, `toString()` = `prefix + body`)
- [X] T049 [P] [US1] `src/main/java/com/team/blog/account/application/HandleCheckResult.java`(record `available`, `reason`, `suggestion`)
- [X] T050 [P] [US1] `src/main/java/com/team/blog/shared/error/HandleViolationException.java`(`code: HandleViolation`, `suggestion: String?` — 걸린 금칙어를 담지 않음) 와 `src/main/java/com/team/blog/shared/error/HandleTakenException.java`(`suggestion`)
- [X] T051 [US1] `src/main/java/com/team/blog/account/application/HandleService.java`: `prefill(String email, Provider)`(이메일 없으면 `user_`+6자리부터, 그 순간 비어 있는 주소), `validateForSignup(String rawHandle, Provider)`(정리 → 접두어 해석·수단 부착(소셜인데 `-` 없으면 수단 접두어) → `HANDLE_PREFIX_MISMATCH` → `HANDLE_INVALID_FORMAT` → `HANDLE_RESERVED`(본문 정확 일치, 제안) → `HANDLE_BANNED_WORD`(본문, `_` 제거 변형 포함, 제안 없음) → `HANDLE_DUPLICATE`(제안); 호출자 가입 트랜잭션 안에서 호출 가능), `checkAvailability(String rawHandle)`(접두어 일치 검사만 뺌), `suggestAlternative(Handle base)`(`@Transactional(propagation = REQUIRES_NEW, readOnly = true)`, 후보 `base`(예약어면 제외), `base_2`… 를 `suggestion-batch-size`(20)개씩 `findHandlesIn` 한 번으로 조회해 비어 있는 첫 값). **기존 회원 주소를 바꾸는 연산은 만들지 않는다**
- [X] T052 [US1] `src/main/java/com/team/blog/account/application/MemberUniqueViolationTranslator.java`(주소 부분): `SignupContext`(record `provider`, `providerUserId?`) 정의, `translate(DataIntegrityViolationException, SignupContext)`가 `ConstraintViolationException.getConstraintName()`으로 분기 — `uq_member_handle` → 새 읽기 트랜잭션에서 `suggestAlternative` → `HandleTakenException(suggestion)`; 소셜 문맥이면 먼저 `(provider, provider_user_id)` 계정 존재를 확인해 `ExistingSocialAccount(memberId)`를 돌려준다(research R-7) — 002에서는 `MemberRepository`의 `auth_identity` 조회 쿼리(`findMemberIdByAuthIdentity(provider, providerUserId)`, 같은 account 모듈 테이블)로 구현하고, 001 T161이 `AuthIdentityRepository`로 바꾼다. 제약 이름은 상수(`uq_member_handle`, `uq_member_nickname`, `uq_auth_identity`). 호출 위치는 **트랜잭션 경계 밖**(Javadoc에 명시)
- [X] T053 [P] [US1] `src/main/java/com/team/blog/account/application/ExistingSocialAccount.java`(record `memberId` — 번역 결과 값)
- [X] T054 [US1] `src/main/java/com/team/blog/account/web/HandleApiController.java`: `GET /api/handles/availability?handle=` → `HandleCheckResult` JSON, `POST /api/handles/suggestion`(JSON `{email}`, CSRF 필수) → `{handle}`(LOCAL 기준 `prefill`, 이메일 형식 아니면 `null`); 두 경로 모두 `RedisRateLimiter`로 키 `account:handle-check:ip:{ip}`, 한도 `blog.account.availability.per-ip-per-minute`(30)/1분, 초과 시 `RateLimitedException`. 로그인 불필요
- [X] T055 [US1] `src/main/java/com/team/blog/shared/error/GlobalExceptionHandler.java`에 매핑 추가: `HandleViolationException` → 400 `{code, suggestion}`, `HandleTakenException` → 409 `HANDLE_TAKEN_CONCURRENTLY` + `suggestion`(SSR 폼의 칸별 안내는 001 컨트롤러가 같은 예외를 받아 화면에 넣음); `src/main/resources/messages.properties`에 data-model §2.1 주소 문구 6개("영문 소문자·숫자·_로 3~36자까지 쓸 수 있어요", "가입 방법과 맞지 않는 주소예요", "사용할 수 없는 주소예요", "사용할 수 없는 단어가 들어 있어요", "이미 사용 중인 주소예요", "방금 다른 분이 이 주소를 사용했어요. {0}는 어떠세요?")
- [X] T056 [P] [US1] 화면 JS `src/main/resources/static/js/account/handle-field.js`: 이메일 입력이 0.5초 멈추면 `POST /api/handles/suggestion`(CSRF 메타 태그 사용) 결과로 주소 칸 채움, 사용자가 주소 칸을 한 번 고치면(`handleTouched`) 이후 자동 채움 중단, 입력 중 대문자 → 소문자, `-`·허용 외 문자 입력 막기, 소셜 화면은 접두어를 고정 글자로 두고 본문만 편집; 칸 속성 `inputmode="latin"`, `autocapitalize="off"`, `lang="en"` 기대(마크업은 001 가입 화면 T134·T163)
- [X] T057 [P] [US1] 화면 JS `src/main/resources/static/js/account/availability.js`: 입력 0.5초 멈춤 후 사용 가능 여부 API 호출, 이전 요청 `AbortController`로 취소, 결과 문구·대안 버튼(누르면 칸에 채움) 표시, 429는 "잠시 후 다시 확인해 주세요"로 표시(사용 중과 구분). 주소·닉네임 칸 공용(닉네임은 US2 T066에서 연결)

**Checkpoint**: T040~T044가 통과한다. 001은 이 시점부터 `HandleService`를 가입에 연결할 수 있다.

---

## Phase 4: User Story 2 - 닉네임을 정하고 규칙에 맞게 검사받는다 (Priority: P1)

**Goal**: `NicknamePolicy` 한곳에서 정리(trim+NFC) → 형식 → 글자 포함 → 예약어 → 금칙어 → 대소문자 무시 중복 순으로 검사하고, 소셜 이름으로 미리 채우며, 동시 요청에서도 한 명만 성공한다.

**Independent Test**: quickstart S3·S4 표의 입력 각각으로 `NicknamePolicy.validate`·`GET /api/nicknames/availability`를 호출해 지정된 오류 코드가 나오고, 어떤 응답에도 금칙어가 나타나지 않음을 확인한다.

### Tests for User Story 2 ⚠️ (먼저 작성, 실패 확인)

- [X] T058 [P] [US2] 단위 테스트 `src/test/java/com/team/blog/account/unit/NicknameRulesTest.java`: `ㅋㅋ`, `김 민서`, `kim!`, `😀kim`, `김`(1자), `가나다라마바사아자차카`(11자) → `NICKNAME_INVALID_FORMAT`; `12345` → `NICKNAME_LETTER_REQUIRED`; `관리자김`, `admin123`, `Official`, `운영팀장`, `adm1n` → `NICKNAME_RESERVED`; `시1발`, `sh1t`, `병1신왕`, `1`→`l` 변형 → `NICKNAME_BANNED_WORD`; `시발점` 통과; NFD `김민서` → NFC 3글자; 검사 순서(첫 실패에서 멈춤); 소셜 이름 정리 `Kim Min-seo`→`KimMinseo`, `김민서 (Minseo)`→`김민서Minseo`, `Christopher Columbus`→`Christophe`, `A`→빈 값
- [X] T059 [P] [US2] 통합 테스트 `src/test/java/com/team/blog/account/integration/NicknamePolicyIT.java`: `Kim` 회원이 있을 때 `kim`·`KIM` → `NICKNAME_DUPLICATE`, `KIM2` 허용; `excludeMemberId` 본인 제외; NFD 입력 저장 후 DB `length(nickname) = 3`; `suggestFromSocialName("Kim")`(이미 있음) → 빈 값; 탈퇴 유예 회원 닉네임은 계속 중복, 익명 처리(`nickname=NULL`) 후에는 사용 가능(FR-026); `NICKNAME_BANNED_WORD` 예외 메시지·`toString`에 금칙어 문자열 없음(SC-006)
- [X] T060 [P] [US2] 통합 테스트 `src/test/java/com/team/blog/account/integration/NicknameConcurrencyIT.java`: 같은 닉네임(대소문자만 다름 포함: `Kim`/`kim`/`KIM` 섞어서) 20개 스레드 동시 저장 → 1건 성공, 나머지는 번역기에서 `NicknameViolationException(NICKNAME_DUPLICATE, concurrent=true)`(SC-003)
- [X] T061 [US2] `src/test/java/com/team/blog/account/integration/AvailabilityApiIT.java`에 닉네임 부분 추가: `GET /api/nicknames/availability?nickname=` → `{available, code}`(09 §3 다섯 코드 중 첫 실패), 로그인 상태(`TestAuth`)면 자기 자신 제외, 31번째 → 429(`account:nickname-check:ip:{ip}` 별도 버킷), 금칙어 거부 응답 본문에 금칙어 문자열이 없음(SC-006)

### Implementation for User Story 2

- [X] T062 [P] [US2] `src/main/java/com/team/blog/account/domain/NicknameViolation.java`(`NICKNAME_INVALID_FORMAT`, `NICKNAME_LETTER_REQUIRED`, `NICKNAME_RESERVED`, `NICKNAME_BANNED_WORD`, `NICKNAME_DUPLICATE`)
- [X] T063 [US2] `src/main/java/com/team/blog/account/domain/NicknameRules.java`(순수 함수): `normalize(raw)` = `strip()` → `Normalizer.normalize(NFC)`; 형식 상수 `^[가-힣a-zA-Z0-9]{2,10}$`, 글자 포함 상수 `[가-힣a-zA-Z]`(DB `ck_member_nickname`과 같은 문자열); 예약어 포함 검사(소문자·NFC 목록을 `TextVariants` 4변형에 대해 **포함** 검사, 예외 목록 미적용); 금칙어 검사는 `BannedWordFilter`를 인자로 받아 호출; 소셜 이름 정리(NFC → `가-힣a-zA-Z0-9` 외 제거 → 앞 10자)
- [X] T064 [P] [US2] `src/main/java/com/team/blog/account/domain/Nickname.java`(값 `value`(NFC), 비교용 `lowerKey()`), `src/main/java/com/team/blog/account/application/NicknameCheckResult.java`(record `normalized`, `violation?`), `src/main/java/com/team/blog/shared/error/NicknameViolationException.java`(`code`, `concurrent` — 금칙어를 담지 않음)
- [X] T065 [US2] `src/main/java/com/team/blog/account/application/NicknamePolicy.java`: `validate(String raw, Long excludeMemberId)` → `Nickname`(① trim+NFC ② 형식 ③ 글자 포함 ④ 예약어 ⑤ 금칙어 ⑥ 중복(`existsByNicknameIgnoreCaseExcluding`), 첫 실패에서 `NicknameViolationException`), `check(raw, excludeMemberId)` → `NicknameCheckResult`, `suggestFromSocialName(String displayName)` → `Optional<String>`(정리 결과가 `check` 통과 시만). 가입 2종(001)·변경(US3)이 같은 정책을 쓴다(FR-020)
- [X] T066 [US2] `src/main/java/com/team/blog/account/application/MemberUniqueViolationTranslator.java`에 `uq_member_nickname` 분기 추가 → `NicknameViolationException(NICKNAME_DUPLICATE, concurrent = true)`("방금 다른 분이 이 닉네임을 사용했어요")
- [X] T067 [US2] `src/main/java/com/team/blog/account/web/NicknameApiController.java`: `GET /api/nicknames/availability?nickname=` → `{available, code}`, `CurrentUserProvider`로 로그인 회원이면 `excludeMemberId` = 본인, 키 `account:nickname-check:ip:{ip}` 1분 30회, 초과 시 `RateLimitedException`; `static/js/account/availability.js`를 닉네임 칸에도 연결; `GlobalExceptionHandler`에 `NicknameViolationException` → 400(경합이면 409) `{code}` 매핑 추가; `messages.properties`에 닉네임 문구 5개 + 경합 문구("한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)", "한글이나 영문을 1자 이상 넣어 주세요", "사용할 수 없는 닉네임이에요", "사용할 수 없는 단어가 들어 있어요", "이미 사용 중인 닉네임이에요", "방금 다른 분이 이 닉네임을 사용했어요")

**Checkpoint**: US1·US2가 모두 동작한다 → 001-auth 가입 흐름(001 Phase 3 이후)을 시작할 수 있다.

---

## Phase 5: User Story 3 - 닉네임을 바꾸고 30일 동안은 다시 못 바꾼다 (Priority: P2)

**Goal**: 본인만 닉네임을 바꿀 수 있고, 바꾼 뒤 30일은 다시 못 바꾸며, 같은 값 재저장은 무시, 대소문자만 바꿔도 변경으로 친다. 003 `ProfileService`가 호출할 `NicknameChangeService`를 제공한다.

**Independent Test**: 닉네임 변경 → 바로 재변경(409) → 이전 닉네임으로 다른 회원 저장(허용) → `MutableClock` 30일 이동 후 변경(허용)을 서비스 통합 테스트로 확인한다(화면·`PATCH /api/me/profile`은 003).

### Tests for User Story 3 ⚠️ (먼저 작성, 실패 확인)

- [ ] T068 [P] [US3] 통합 테스트 `src/test/java/com/team/blog/account/integration/NicknameChangeIT.java`: 가입 후 첫 변경 → `CHANGED`, `nickname_changed_at` = 시계 시각(US3-1); 곧바로 다른 값 → `NicknameChangeTooSoonException(nextAllowedAt = changedAt + 30일)`, 값 그대로(US3-2, SC-007); 같은 값 재저장 → `UNCHANGED`, `nickname_changed_at` 그대로, 제한 중이어도 성공(US3-4); 바꾼 이전 닉네임을 다른 회원이 즉시 사용 가능(US3-3, FR-025); 30일 경과 후 변경 성공, 이어서 `kim`→`Kim`(대소문자만)도 `CHANGED`이고 자기 자신 때문에 중복 오류 없음(US3-5); `nextAllowedAt`(제한 중이면 값, 아니면 empty); 호출자 트랜잭션 안에서 예외 시 호출자 전체 롤백(`REQUIRED`)
- [ ] T069 [P] [US3] 통합 테스트 `src/test/java/com/team/blog/account/integration/NicknameChangeOwnershipIT.java`(헌법 III·VI): `change(memberIdA, …)`는 A만 바꾸고 B 행은 그대로; `AccountGuard.requireLoggedIn(Optional.empty())` → `LoginRequiredException`; 같은 회원의 변경 2건 동시(제한 없는 상태) → 1건 `CHANGED`, 다른 1건 `NicknameChangeTooSoonException`(행 잠금); 서로 다른 회원이 같은 닉네임으로 동시 변경 → 1건 성공, 다른 쪽은 번역기 `translate(e)`로 `NICKNAME_DUPLICATE`(concurrent)

### Implementation for User Story 3

- [ ] T070 [P] [US3] `src/main/java/com/team/blog/account/application/NicknameChangeResult.java`(sealed: `Changed(Instant changedAt)` / `Unchanged`) 와 `src/main/java/com/team/blog/shared/error/NicknameChangeTooSoonException.java`(`nextAllowedAt: Instant`)
- [ ] T071 [US3] `src/main/java/com/team/blog/account/domain/Member.java`에 `changeNickname(Nickname, Instant now)` 추가(`nickname`, `nickname_changed_at = now`, `updated_at = now`); `handle`은 여전히 변경 메서드 없음
- [ ] T072 [US3] `src/main/java/com/team/blog/account/application/NicknameChangeService.java`: `change(long memberId, String raw)`(`@Transactional(propagation = REQUIRED)`) — `findByIdForUpdate` → 정리값이 현재 닉네임과 글자까지 같으면 `Unchanged` → `nickname_changed_at + blog.account.nickname.change-cooldown(30d)`이 `Clock` 현재보다 미래면 `NicknameChangeTooSoonException` → `NicknamePolicy.validate(raw, memberId)` → `changeNickname` + `saveAndFlush`; `nextAllowedAt(long memberId)` → `Optional<Instant>`. `memberId`는 호출자가 `CurrentUser`에서 꺼낸 값만(요청 본문 회원 ID를 받는 연산 없음)
- [ ] T073 [US3] `src/main/java/com/team/blog/account/application/MemberUniqueViolationTranslator.java`에 닉네임 변경용 `translate(DataIntegrityViolationException e)` 추가(`uq_member_nickname` → `NicknameViolationException(NICKNAME_DUPLICATE, concurrent = true)`, 그 밖은 원래 예외 재던짐)
- [ ] T074 [US3] `src/main/java/com/team/blog/shared/error/GlobalExceptionHandler.java`에 `NicknameChangeTooSoonException` → 409 `NICKNAME_CHANGE_TOO_SOON` + `nextAllowedAt` 매핑 추가, 화면용 날짜 포맷 도우미 `src/main/java/com/team/blog/shared/web/KoreanDateFormatter.java`("M월 d일", `Asia/Seoul`), `messages.properties`에 "다음 변경 가능일: {0}" 추가

**Checkpoint**: 003-profile이 `NicknameChangeService`를 호출할 수 있다.

---

## Phase 6: User Story 4 - 블로그 주소와 닉네임이 일관되게 보이고 찾아진다 (Priority: P2)

**Goal**: `/@주소` 대문자 접속은 301로 소문자 주소에, 없는·탈퇴한 주소는 404, `닉네임 @블로그주소` 표시 조각과 탈퇴자 "탈퇴한 사용자" 표시를 제공한다.

**Independent Test**: `/@Kim755030` → 301 `/@kim755030`, `/@nobody123`·`/@없는주소`·탈퇴 회원 주소 → 404, 표시 조각 렌더링 결과를 확인한다.

### Tests for User Story 4 ⚠️ (먼저 작성, 실패 확인)

- [ ] T075 [P] [US4] 통합 테스트 `src/test/java/com/team/blog/account/integration/BlogAddressRoutingIT.java`: `/@Kim755030` → 301 `Location: /@kim755030`, `/@Kim755030/posts/12?x=1` → `/@kim755030/posts/12?x=1`(존재 확인 전에 실행); `/@nobody123`, `/@없는주소`(URL 인코딩) → 404 공통 화면(본문이 다른 404와 같음); 탈퇴 유예·익명 처리 회원 주소 → 404; 익명 처리된 회원의 주소로 `validateForSignup` → `HANDLE_DUPLICATE` + 대안(US4-3, FR-013), 닉네임은 사용 가능
- [ ] T076 [P] [US4] 단위 테스트 `src/test/java/com/team/blog/account/unit/AuthorDisplayTest.java`: 정상 `fullLabel()` = `김민서 @kim755030`, `shortLabel()` = `김민서`, `blogPath()` = `/@kim755030`; `withdrawnAt` 있음 또는 `nickname = null` → 둘 다 "탈퇴한 사용자", 링크 없음
- [ ] T077 [P] [US4] 통합 테스트 `src/test/java/com/team/blog/account/integration/AuthorFragmentIT.java`: `fragments/author :: byline`·`:: name` 렌더링 결과(정상·탈퇴), 닉네임·주소는 `th:text` 이스케이프; `MemberSummaryQuery.findByIds`가 회원 여러 명을 쿼리 1번으로 가져옴(Hibernate 통계로 확인, N+1 금지)

### Implementation for User Story 4

- [ ] T078 [P] [US4] `src/main/java/com/team/blog/account/application/AuthorDisplay.java`(`of(handle, nickname, withdrawnAt)`, `fullLabel()`, `shortLabel()`, `blogPath()`) 와 `src/main/java/com/team/blog/account/application/BlogOwner.java`(record `memberId`, `handle`, `nickname`, `bio`, `profileImageUrl`)
- [ ] T079 [US4] `src/main/java/com/team/blog/account/application/HandleService.java`에 `canonicalPath(String pathHandle)` 추가(대문자가 있으면 소문자 값, 없으면 empty — 존재 여부는 보지 않음)
- [ ] T080 [US4] `src/main/java/com/team/blog/shared/web/HandlePathCanonicalizer.java`(`OncePerRequestFilter`, 가장 앞 순서로 등록): `/@{handle}`, `/@{handle}/**`의 handle 부분에 대문자가 있으면 그 부분만 소문자로 바꿔 301(나머지 경로·쿼리 유지)
- [ ] T081 [US4] `src/main/java/com/team/blog/account/application/BlogOwnerResolver.java`(`resolve(String handle)` → `Optional<BlogOwner>`: 형식(`HandleRules` 상수)에 맞지 않으면 조회 없이 empty, `findActiveByHandle`로 `status <> 'WITHDRAWN'`만) 와 `src/main/java/com/team/blog/account/application/MemberSummaryQuery.java`(`findByIds(Collection<Long>)` → `Map<Long, AuthorDisplay>`, `IN` 한 번, 탈퇴·익명 회원 포함)
- [ ] T082 [P] [US4] 표시 조각 `src/main/resources/templates/fragments/author.html`(`byline(author)` → `닉네임 @주소`(주소는 `/@주소` 링크), `name(author)` → 닉네임만; 탈퇴는 "탈퇴한 사용자"만, `th:text`만 사용)
- [ ] T083 [US4] 블로그 진입 자리표시 `src/main/java/com/team/blog/discovery/web/BlogPageController.java` + `src/main/resources/templates/blog/home.html`: `GET /@{handle}` → `BlogOwnerResolver.resolve` 결과가 없으면 `NotFoundException`(404), 있으면 상단에 `byline` 조각만 그린다. 글 목록·프로필 상단 내용은 009·003이 이 컨트롤러·템플릿을 채운다(plan에 컨트롤러가 없어 추가한 최소 구현)

**Checkpoint**: 모든 스토리(US1~US4)가 각자 테스트로 검증된다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T084 [P] `handle` 변경 경로 점검: `src/main/java` 전체에서 `member.handle`을 UPDATE하는 JPQL·네이티브 쿼리·메서드가 없는지 확인하는 테스트 `src/test/java/com/team/blog/account/unit/NoHandleUpdatePathTest.java`(소스 검색 기반, SC-004)
- [ ] T085 [P] 금칙어 비노출 점검: 모든 오류 응답·로그 출력에 금칙어가 없는지 `src/test/java/com/team/blog/account/integration/BannedWordLeakIT.java`(로그 캡처 + 주소·닉네임 API·예외 응답 본문 검사, SC-006)
- [ ] T086 [P] 성능 확인: 사용 가능 여부 API 서버 응답 p95 100ms 이내(회원 1만 행 기준, SC-008)를 `src/test/java/com/team/blog/account/integration/AvailabilityLatencyIT.java`로 측정하고 `EXPLAIN`으로 `uq_member_handle`·`uq_member_nickname` 인덱스 사용 확인
- [ ] T087 전체 테스트 `./gradlew test` 통과 확인, quickstart.md S1~S7 중 002 단독으로 가능한 항목(S1-1 서버 부분, S2-1~4 서비스 부분, S3, S5, S6, S7-1) 수행. 가입 화면 수동 항목(S1-2~4, S2-5, S7-2)은 001 T134·T163 완료 후 001 Polish(T175)에서 함께 확인

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존 없음. 저장소 전체 골격이므로 가장 먼저.
- **Foundational (Phase 2)**: Setup 완료 후. **모든 스토리를 막는다.** 001-auth 전체도 이 단계에 의존한다.
- **US1 (Phase 3), US2 (Phase 4)**: Foundational 완료 후. 서로 독립(주소/닉네임). 단 `MemberUniqueViolationTranslator`·`AvailabilityApiIT`·`GlobalExceptionHandler`·`messages.properties` 파일을 함께 고치므로 같은 파일 작업은 순서대로(US1 → US2).
- **US3 (Phase 5)**: US2(`NicknamePolicy`) 완료 후.
- **US4 (Phase 6)**: Foundational 완료 후 시작 가능(US1의 `HandleService`에 `canonicalPath`를 추가하므로 T079는 T051 이후).
- **Polish (Phase 7)**: 원하는 스토리 완료 후.

### 001-auth와의 순서

- 001의 Setup(T101~)은 002 Phase 1·2(T001~T039) 완료가 전제다.
- 001 가입(US1·US3)은 002 US1·US2(T040~T067) 완료가 전제다.
- 003-profile은 002 US3, 009·010·014는 002 US4에 의존한다.

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한다.
- 도메인 값·규칙 → Service → 번역기 → 컨트롤러·예외 매핑 → JS 순서.

### Parallel Opportunities

- Setup: T004, T005, T006, T008, T009 병렬(T001~T003 뒤).
- Foundational: T011~T015, T016~T018, T020, T022~T027, T030, T031, T034~T037, T039 병렬. T019·T021·T028·T029·T032·T038은 각 묶음의 앞선 작업 뒤.
- US1 테스트 T040~T044 병렬, 구현 T045·T046·T048·T049·T050·T053·T056·T057 병렬.
- US2 테스트 T058~T060 병렬, 구현 T062·T064 병렬.
- US4는 US2·US3과 병렬 진행 가능(다른 파일).

---

## Parallel Example: User Story 1

```bash
# 테스트 먼저 (동시에)
Task: "T040 HandleRulesTest in src/test/java/com/team/blog/account/unit/HandleRulesTest.java"
Task: "T041 HandleSignupIT in src/test/java/com/team/blog/account/integration/HandleSignupIT.java"
Task: "T042 HandleConcurrencyIT in src/test/java/com/team/blog/account/integration/HandleConcurrencyIT.java"
Task: "T043 HandleImmutabilityIT in src/test/java/com/team/blog/account/integration/HandleImmutabilityIT.java"
Task: "T044 AvailabilityApiIT(주소) in src/test/java/com/team/blog/account/integration/AvailabilityApiIT.java"

# 값·예외 (동시에)
Task: "T045 HandlePrefix", "T046 HandleViolation", "T048 Handle", "T049 HandleCheckResult", "T050 Handle 예외 2종", "T053 ExistingSocialAccount"

# 화면 JS (동시에, Service와 무관)
Task: "T056 handle-field.js", "T057 availability.js"
```

## Parallel Example: User Story 2

```bash
Task: "T058 NicknameRulesTest in src/test/java/com/team/blog/account/unit/NicknameRulesTest.java"
Task: "T059 NicknamePolicyIT in src/test/java/com/team/blog/account/integration/NicknamePolicyIT.java"
Task: "T060 NicknameConcurrencyIT in src/test/java/com/team/blog/account/integration/NicknameConcurrencyIT.java"
Task: "T062 NicknameViolation", "T064 Nickname·NicknameCheckResult·NicknameViolationException"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2 Foundational (저장소 골격 완성).
2. Phase 3 US1(블로그 주소) → **멈추고 검증**: T040~T044 통과.
3. Phase 4 US2(닉네임)까지 끝내면 001-auth 가입이 필요한 두 규칙이 모두 준비된다. **002의 MVP 범위 = US1 + US2**(둘 다 P1이고 001 가입의 선행 조건).

### Incremental Delivery

1. Setup + Foundational → 골격(001·003 이후 모든 기능의 기반).
2. US1 → 주소 규칙·API·JS (001 이메일 가입 연결 가능).
3. US2 → 닉네임 규칙·API (001 이메일·소셜 가입 완성 가능).
4. US3 → 닉네임 변경 (003 프로필 연결 가능).
5. US4 → `/@주소` 정규화·404·표시 조각 (009·010·014 연결 가능).

### Parallel Team Strategy

- Foundational 완료 후: 개발자 A US1 → US3, 개발자 B US2, 개발자 C US4. 이어서 001-auth를 시작한다.

---

## Notes

- [P] = 다른 파일, 끝나지 않은 작업에 의존하지 않음. [Story] = 추적용 스토리 표시.
- 새 테이블·컬럼·마이그레이션은 만들지 않는다. V1(T007)은 51 원문 그대로이며, 형식 정규식은 DB CHECK와 같은 문자열 상수다(헌법 II).
- 블로그 주소를 바꾸는 연산·경로를 만들지 않는다(FR-011).
- 걸린 금칙어는 응답·화면·로그 어디에도 넣지 않는다(SC-006).
- 작업 또는 논리 묶음마다 커밋한다.
