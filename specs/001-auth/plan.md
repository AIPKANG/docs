# Implementation Plan: 로그인·로그아웃 (이메일 가입 + Google + GitHub)

**Branch**: `001-auth` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-auth/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Tier A 공통 필수 C-AUTH-1을 구현한다. 이메일 가입(인증 필수)·Google·GitHub 세 로그인 수단을 제공하고, 로그인 수단이 다르면 같은 이메일이라도 별도 계정이다(L-1). 기술 접근은 **Thymeleaf SSR + Spring Security 폼 로그인/OAuth2 Client + Spring Session Data Redis(인덱스 저장소, 14일)**이며, 계정 데이터는 공통 ERD(51)의 `member`·`auth_identity`·`member_agreement`·`member_suspension`을 수정 없이 쓰고, 인증·재설정 토큰과 요청 제한 카운터는 Redis TTL 키로 둔다. 쓰기 차단(인증 전·탈퇴 유예)은 `shared.security.AccountGuard`를 통해 각 Service가 다시 검사한다. 소셜 첫 로그인 시 같은 이메일의 다른 수단 계정이 있으면 합치지 않고 안내만 한다(FR-033, 사용자 확정 2026-10-07).

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1 (2026-10-07 최신 GA, 사용자 확정 — research R-2), Gradle 9.8.0 Wrapper

**Primary Dependencies**: Spring Web MVC, Thymeleaf, Spring Security 7(폼 로그인·OAuth2 Client·CSRF), Spring Session Data Redis(인덱스 저장소), Spring Data JPA, Flyway, Spring Mail(JavaMailSender), Bean Validation

**Storage**: PostgreSQL 18(공통 V1 기준선, 새 마이그레이션 없음) / Redis(세션, 토큰, 요청 제한 카운터; AOF `everysec`, `noeviction`) / 브라우저 IndexedDB(로그아웃 시 삭제 대상만)

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test(MockMvc `csrf()`·`oauth2Login()`), Testcontainers(PostgreSQL·Redis·Mailpit)

**Target Platform**: Linux 서버(Docker Compose), 브라우저 375px~데스크톱

**Project Type**: web-service (모듈러 모놀리스, SSR)

**Performance Goals**: 로그인·가입 서버 응답 p95 500ms 이내(BCrypt 강도 10 포함, 메일 발송 제외 — 메일은 커밋 후). 쓰기 요청의 인증 상태 검사는 PK 조회 1회

**Constraints**: 비밀번호·토큰 원문 저장·로그 금지(FR-014), 트랜잭션 안 외부 호출 금지(헌법 V), 정책 수치 전부 설정값(헌법 II), 가입 여부 비노출(SC-006), 세션 고정 방지·CSRF·쿠키 `HttpOnly; Secure; SameSite=Lax`

**Scale/Scope**: 팀 프로젝트 규모(회원 수천~1만), 화면 9개(가입, 인증 안내, 인증 결과, 로그인, 소셜 가입 마무리, 비밀번호 찾기, 재설정, 복구 진입, 403 안내), 메일 3종

미정 항목(NEEDS CLARIFICATION)은 없다. 팀 확정 대기 항목은 research.md "남은 확인 사항"(U-1~U-6)에 있고 계획 진행을 막지 않는다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 내용 | Phase 0 전 | Phase 1 후 |
|---|---|---|---|
| I. 하나의 배포 단위, 모듈러 모놀리스 | 단일 Spring Boot 앱. 인증은 `account` 모듈 + `shared.security`. 다른 모듈은 `AccountGuard`·`CurrentUser`·`SessionRevoker`·이벤트로만 사용([account-service.md](./contracts/account-service.md)). 업무 규칙(비밀번호 정책, 상태 판정, 잠금)은 Service/domain에 둠 | PASS | PASS |
| II. 공통은 바꾸지 않고, 확장은 추가만 (NON-NEGOTIABLE) | 새 테이블·컬럼·마이그레이션 없음, 51 정의 그대로(약관은 `member_agreement`). 모든 수치(24h, 30m, 1분/10회, 5회/15분, 20회/1분, 14일, 10분)는 `blog.auth.*` 설정값 | PASS | PASS |
| III. 서버가 권한을 지킨다 (NON-NEGOTIABLE) | 현재 사용자는 SecurityContext에서만. 인증 전·탈퇴 유예 차단은 Service의 `AccountGuard`가 42 §3 순서로 판정, 화면 숨김은 보조. 403은 계정 상태에만 사용(42 §4) | PASS | PASS |
| IV. 사용자 입력은 안전하게 보여준다 | 닉네임·소셜 이름은 이스케이프 출력·09 정리 규칙. 로그인 후 이동은 상대 경로만. 보안 헤더(CSP·nosniff·Referrer-Policy) 공통 적용. OAuth secret·SMTP 비밀번호는 환경 변수 | PASS | PASS |
| V. 부가 기능은 핵심을 막지 않는다 | 메일은 커밋 후 발송, 실패해도 가입·요청 결과 유지(재발송으로 회복). 중복 가입은 DB UNIQUE로 1건 보장(동시 20회). 트랜잭션 안 외부 HTTP·SMTP 없음 | PASS | PASS |
| VI. 실제 환경으로 검증한다 | Testcontainers PostgreSQL·Redis·Mailpit, H2 미사용. 수용 시나리오를 [quickstart.md](./quickstart.md) S1~S6과 통합 테스트로 그대로 옮김 | PASS | PASS |
| 기술 제약 표 | Java 21, PostgreSQL, Redis(Spring Session 14일), BCrypt, 이메일+Google+GitHub, JUnit5·Testcontainers·Spring Security Test, Docker Compose | PASS (Boot 버전은 팀 확정 대기) | PASS |

**결과: 위반 없음.** Complexity Tracking 기재 사항 없음.

Phase 1 재확인 메모
- 문서 간 불일치 1건을 51 기준으로 정리: 07 §3·§8/source-notes의 `member.terms_agreed_at`·`privacy_agreed_at` → 51의 `member_agreement`. 헌법 개발 흐름 4(결정 기록 우선)와 지시(51·52 그대로)에 맞다.
- 52 A-3(`SUSPENDED` 삭제, 팀 결정 대기)은 정지 판정을 `member_suspension` 기준으로 설계해 어느 결정이든 공통 ERD 변경 없이 대응한다(research R-9).
- 토큰을 Redis 키에 해시로 두는 것은 07의 키 이름 패턴 안에서의 세부 선택이며 FR-014를 강화한다(research R-5).

## Project Structure

### Documentation (this feature)

```text
specs/001-auth/
├── plan.md              # 이 파일 (/speckit-plan 출력)
├── research.md          # Phase 0 출력
├── data-model.md        # Phase 1 출력
├── quickstart.md        # Phase 1 출력
├── contracts/           # Phase 1 출력
│   ├── web-routes.md        # SSR 화면·HTTP 경로와 결과
│   ├── account-service.md   # account 모듈 공개 Service·이벤트·메일 계약
│   └── redis-keys.md        # Redis 키·TTL·한도
├── checklists/
│   └── requirements.md
├── source-notes.md
├── spec.md
└── tasks.md             # Phase 2 출력 (/speckit-tasks — 이 명령에서 만들지 않음)
```

### Source Code (repository root)

```text
src/main/java/com/team/blog/
├── account/
│   ├── web/            AuthController(가입·인증·로그인 화면), SocialSignupController,
│   │                   PasswordResetController, AccountRestoreEntryController
│   ├── application/    EmailSignupService, EmailVerificationService, SocialLoginService,
│   │                   SocialSignupService, PasswordResetService, LoginAttemptService,
│   │                   AccountStatusChecker, SessionRevoker, HandleService·NicknamePolicy(규칙은 002)
│   ├── domain/         Member, AuthIdentity, MemberAgreement, MemberSuspension(읽기),
│   │                   Provider, MemberStatus, PasswordPolicy, PendingSocialSignup
│   └── infra/          MemberRepository, AuthIdentityRepository, MemberAgreementRepository,
│                       MemberSuspensionRepository, RedisTokenStore, RedisRateLimiter,
│                       GoogleOidcUserService, GitHubOAuth2UserService, SmtpMailSender
└── shared/
    ├── security/       SecurityConfig, CurrentUser, CurrentUserProvider, AccountGuard,
    │                   RedirectTargetValidator, RestoreOnlySessionFilter, SessionCookieRefreshFilter
    ├── error/          LoginRequiredException, AccountStatusException, GlobalExceptionHandler
    └── event/          MemberSignedUp, VerificationMailRequested, EmailVerified,
                        PasswordResetMailRequested, PasswordResetCompleted

src/main/resources/
├── application.yml     blog.auth.* 설정값, spring.session (indexed, 14d), OAuth 등록
├── templates/auth/     signup, verify-sent, verify-result, login, social-signup,
│                       password-forgot, password-reset, forbidden-unverified
├── templates/mail/     verify, reset, social-only
├── static/js/auth/     password-rules.js(✓ 표시), auth-logout.js(IndexedDB 정리)
├── security/common-passwords.txt
└── db/migration/       (V1 기준선만 — 이 기능은 추가 없음)

src/test/java/com/team/blog/account/
├── integration/        이메일 가입·인증, 로그인·잠금, 로그아웃, 소셜, 재설정, 계정 상태 (Testcontainers)
└── unit/               PasswordPolicy, RedirectTargetValidator, RateLimiter 키 계산
```

**Structure Decision**: 단일 프로젝트(저장소 루트, Gradle 기본 — research R-16) 모듈러 모놀리스. 02 §3의 `com.team.blog` 패키지 구조에서 `account` 모듈과 `shared.security`·`shared.error`·`shared.event`를 이 기능이 채운다. 프런트엔드는 별도 프로젝트 없이 Thymeleaf 템플릿과 정적 JS로 둔다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (Constitution Check 위반 없음).

## Phase 요약

- **Phase 0** → [research.md](./research.md): R-1~R-17 결정, 남은 확인 사항 U-1~U-6.
- **Phase 1** → [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).
- **에이전트 컨텍스트 갱신**: 이 프로젝트의 speckit-plan 스킬 정의에는 해당 단계가 없고, `CLAUDE.md`를 만들거나 고치지 않기 위해 실행하지 않았다.
- **다음 단계**: `/speckit-tasks`. 002(블로그 주소·닉네임) 규칙에 의존하므로 tasks에서 순서를 정한다(research R-17).
