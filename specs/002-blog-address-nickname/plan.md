# Implementation Plan: 블로그 주소(아이디)와 닉네임

**Branch**: `002-blog-address-nickname` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-blog-address-nickname/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Tier A 공통 필수 C-AUTH-2(주소 변경 불가·닉네임 규칙)와 C-BLOG-1(`/@주소`)의 기반 규칙을 구현한다. 블로그 주소는 이메일 `@` 앞부분으로 서버가 미리 채우고(08 §3 10단계, 소셜은 `go-`/`gi-` 접두어), 가입할 때만 고칠 수 있으며 이후 어떤 경로로도 바뀌지 않는다. 닉네임은 `NicknamePolicy` 한곳에서 정리(trim+NFC) → 형식 → 글자 포함 → 예약어 → 금칙어(4가지 변형·예외 목록) → 대소문자 무시 중복 순으로 검사하고, 바꾼 뒤 30일은 다시 바꿀 수 없다. 기술 접근은 001-auth와 같은 **Thymeleaf SSR + Spring Boot 4.1.1 모듈러 모놀리스**이며, 002는 `account` 모듈에 `HandleService`·`NicknamePolicy`·`NicknameChangeService`·`BlogOwnerResolver`·`MemberUniqueViolationTranslator`와 `shared.text.BannedWordFilter`를 만들어 001(가입)·003(프로필)·009/010/014(표시)에 공개 Service로 내보낸다(001 research R-17). 공통 ERD(51)의 `member.handle`·`nickname`·`nickname_changed_at`을 수정 없이 쓰고, 동시 가입·변경은 `uq_member_handle`·`uq_member_nickname`이 최종 보장한다.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1 (사용자 확정 2026-10-07, 001 research R-2와 같음), Gradle Wrapper 9.8.0 (Kotlin DSL)

**Primary Dependencies**: Spring Web MVC, Thymeleaf, Spring Security 7(001 설정 재사용: CSRF, `CurrentUser`), Spring Session Data Redis, Spring Data JPA, Flyway, Bean Validation, `java.text.Normalizer`(NFC). 새 외부 라이브러리 없음

**Storage**: PostgreSQL 18(공통 V1 기준선 `member` 그대로, 새 마이그레이션 없음) / Redis(사용 가능 여부 확인 요청 제한 카운터만) / 클래스패스 설정 파일(금칙어·예외 목록), `application.yml`(예약어 목록·수치)

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers(PostgreSQL 18·Redis). 08 §3·09 §2~§7 예시표를 매개변수 테스트로 옮김

**Target Platform**: Linux 서버(Docker Compose), 브라우저 375px~데스크톱

**Project Type**: web-service (모듈러 모놀리스, SSR)

**Performance Goals**: 사용 가능 여부 API 서버 응답 p95 100ms 이내(인덱스 조회 1~2회) → 입력 멈춤 후 1초 안 표시(SC-008). 주소 대안 계산은 `IN` 묶음 조회 1회(보통). 금칙어 검사는 입력 길이 기준 부분 문자열 조회(목록 크기와 무관)

**Constraints**: 블로그 주소 불변(변경 경로 자체 없음), 걸린 금칙어 비노출(SC-006), 형식 정규식은 DB CHECK와 같은 문자열 상수, 정책 수치·목록은 설정값(헌법 II), 경합 번역은 트랜잭션 경계 밖, 이메일은 URL에 싣지 않음

**Scale/Scope**: 회원 수천~1만, 금칙어 목록 수천 단어 이하. 화면 영향: 가입 2종(001)의 주소·닉네임 칸, 설정 화면 닉네임 칸(003), 표시 조각 2개, JSON API 3개, `/@{handle}` 경로 정규화

미정 항목(NEEDS CLARIFICATION)은 없다. 원문에 없는 세부는 research.md에서 기본값을 정했고, 팀 확인 권장 항목은 research.md "남은 확인 사항"(U-1~U-6)에 있으며 계획 진행을 막지 않는다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 내용 | Phase 0 전 | Phase 1 후 |
|---|---|---|---|
| I. 하나의 배포 단위, 모듈러 모놀리스 | 단일 Spring Boot 앱. 규칙은 `member`를 소유한 `account` 모듈(+ 공용 `shared.text`)에 둔다. 001·003·009·010·014는 [account-identity-service.md](./contracts/account-identity-service.md)의 공개 Service·값 객체·Thymeleaf 조각으로만 사용하고 `MemberRepository`를 직접 쓰지 않는다. 검사·상태 전이(30일 제한)는 Service/domain에 둠 | PASS | PASS |
| II. 공통은 바꾸지 않고, 확장은 추가만 (NON-NEGOTIABLE) | 새 테이블·컬럼·인덱스·마이그레이션 없음, 51의 `member` 제약 그대로(`uq_member_handle`, `ck_member_handle`, `uq_member_nickname`, `ck_member_nickname`, `ck_member_nickname_null`). 30일·1분 30회·예약어·금칙어·30자·대안 묶음 크기 모두 `blog.account.*`/`blog.text.*` 설정값 | PASS | PASS |
| III. 서버가 권한을 지킨다 (NON-NEGOTIABLE) | 닉네임 변경 대상은 `CurrentUser`의 memberId만(요청 값 무시), 화면 비활성화와 별도로 서버가 30일·규칙을 다시 검사. 가입 시 화면 확인과 별도로 서버 재검사 + DB UNIQUE. 없는 주소·탈퇴 회원 주소는 똑같이 404 | PASS | PASS |
| IV. 사용자 입력은 안전하게 보여준다 | 닉네임은 NFC 정리 후 허용 문자만(보이지 않는 문자·방향 문자·제어 문자는 형식에서 거부), 출력은 `th:text` 이스케이프만. 주소는 소문자·영숫자·`_`만 | PASS | PASS |
| V. 부가 기능은 핵심을 막지 않는다 | 트랜잭션 안 외부 호출 없음. 동시 같은 주소·닉네임 요청은 DB UNIQUE로 1건만 성공(SC-003), 진 쪽은 안내. 사용 가능 여부 API 실패(429 등)는 가입 제출의 서버 검사로 대체되어 가입을 막지 않는다 | PASS | PASS |
| VI. 실제 환경으로 검증한다 | Testcontainers PostgreSQL·Redis, H2 미사용(`lower()` 함수 인덱스·정규식 CHECK 동작 확인 필요). 본인만 변경·동시성·404는 통합 테스트. 수용 시나리오를 [quickstart.md](./quickstart.md) S1~S7로 옮김 | PASS | PASS |
| 기술 제약 표 | Java 21, Spring Boot 4.1.x, Gradle(Wrapper, Kotlin DSL), PostgreSQL, Redis, Flyway, JUnit 5·Testcontainers·Spring Security Test | PASS | PASS |

**결과: 위반 없음.** Complexity Tracking 기재 사항 없음.

Phase 1 재확인 메모
- 001과의 정합: 001 plan의 패키지 이름(`account.application`의 `HandleService`·`NicknamePolicy`), 매개변수 이름(`POST /signup`·`POST /signup/social`의 `handle`, `nickname`), 001 R-8(DB 제약 최종 보장, 소셜 동시 완료는 기존 계정 로그인)을 그대로 따른다. 소셜 마무리의 `handle`은 본문만 받고 접두어는 서버가 붙인다(research R-5) — 001 web-routes의 "`go-`/`gi-` 고정 + 본문 수정" 화면 설명과 일치한다.
- 소셜 동시 제출에서 `uq_member_handle`이 `uq_auth_identity`보다 먼저 걸릴 수 있는 점을 발견해, 번역기가 소셜 문맥에서 계정 존재를 먼저 확인하도록 정했다(research R-7). 001 R-8의 기대 결과를 바꾸지 않는다.
- 08 원문에 없는 보조 API `POST /api/handles/suggestion`을 추가했다(research R-4, U-4). 서버 한곳 계산으로 SC-001을 보장하고 이메일을 URL에 싣지 않기 위함이며, 공통 ERD·다른 기능 계약에는 영향이 없다.
- 요청 제한 초과 응답 429는 42 §4 표 밖이다(research R-14, U-2). 원칙 위반은 아니며(42는 권한 응답 표) 팀 확인 항목으로 남긴다.
- 52 점검 항목 중 이 기능의 컬럼을 건드리는 것은 없다(research R-17).

## Project Structure

### Documentation (this feature)

```text
specs/002-blog-address-nickname/
├── plan.md              # 이 파일 (/speckit-plan 출력)
├── research.md          # Phase 0 출력
├── data-model.md        # Phase 1 출력
├── quickstart.md        # Phase 1 출력
├── contracts/           # Phase 1 출력
│   ├── account-identity-service.md   # 001·003·009 등에 내보내는 Service·값·예외 계약
│   └── web-routes.md                 # JSON API 3개, 가입 화면 칸, /@{handle} 정규화, 표시 조각
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
│   ├── web/            HandleApiController(availability·suggestion),
│   │                   NicknameApiController(availability)
│   │                   (가입 화면 칸은 001 AuthController·SocialSignupController, 설정 화면은 003)
│   ├── application/    HandleService, NicknamePolicy, NicknameChangeService,
│   │                   BlogOwnerResolver, MemberSummaryQuery, MemberUniqueViolationTranslator,
│   │                   AuthorDisplay, BlogOwner, HandleCheckResult, NicknameCheckResult,
│   │                   NicknameChangeResult, AccountIdentityProperties(@ConfigurationProperties)
│   ├── domain/         Handle, HandlePrefix, HandleRules(08 §3 1~9단계·형식),
│   │                   Nickname, NicknameRules(정리·형식·글자·예약어·금칙어),
│   │                   HandleViolation, NicknameViolation (오류 코드 enum),
│   │                   Member(handle 불변 — 001과 공유 엔터티)
│   └── infra/          MemberRepository(001과 공유: existsByHandle, findHandlesIn,
│                       existsByNicknameIgnoreCaseExcluding, findByIdForUpdate, findActiveByHandle),
│                       RedisRateLimiter(001 소유, 재사용)
└── shared/
    ├── text/           BannedWordFilter, TextVariants, WordListLoader
    ├── web/            HandlePathCanonicalizer (/@{handle} 대문자 → 301)
    └── error/          HandleViolationException, HandleTakenException,
                        NicknameViolationException, NicknameChangeTooSoonException,
                        RateLimitedException → GlobalExceptionHandler 매핑 추가

src/main/resources/
├── application.yml     blog.account.handle.*, blog.account.nickname.*,
│                       blog.account.availability.*, blog.text.banned-words.*
├── policy/             banned-words.txt, banned-words-exceptions.txt (팀 검토본)
├── templates/fragments/author.html   byline, name 조각
└── static/js/account/  handle-field.js(자동 채움·소문자·입력 제한·수정 감지),
                        availability.js(0.5초 디바운스 확인)

src/test/java/com/team/blog/
├── account/unit/       HandleRulesTest, NicknameRulesTest
├── account/integration/ HandleSignupIT, HandleConcurrencyIT, NicknamePolicyIT,
│                        NicknameChangeIT, BlogAddressRoutingIT, AvailabilityApiIT
└── shared/text/        BannedWordFilterTest, TextVariantsTest
src/test/resources/policy/  테스트용 금칙어·예외 목록
```

**Structure Decision**: 001과 같은 단일 Gradle 프로젝트(저장소 루트) 모듈러 모놀리스, 02 §3의 `com.team.blog` 패키지 구조. 블로그 주소·닉네임은 `member` 컬럼이므로 `account` 모듈에 두고, 소개(003)·향후 댓글 필터와 함께 쓰는 금칙어 필터만 `shared.text`에 둔다. `Member` 엔터티와 `MemberRepository`는 001과 같은 클래스를 공유하며 002가 위 메서드를 추가한다. 프런트엔드는 Thymeleaf 조각과 정적 JS 두 개뿐이다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (Constitution Check 위반 없음).

## Phase 요약

- **Phase 0** → [research.md](./research.md): R-1~R-18 결정, 남은 확인 사항 U-1~U-6.
- **Phase 1** → [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).
- **에이전트 컨텍스트 갱신**: 이 프로젝트의 speckit-plan 스킬 정의에는 해당 단계가 없고, `CLAUDE.md`를 만들거나 고치지 않기 위해 실행하지 않았다(001과 같음).
- **구현 순서 제안(tasks에서 확정)**: 002의 순수 규칙(`HandleRules`, `NicknameRules`, `BannedWordFilter`)과 `HandleService`·`NicknamePolicy`를 001 가입 흐름보다 먼저 또는 함께 만든다(001 research R-17). `NicknameChangeService`는 003보다 먼저, 표시 조각은 009·010·014보다 먼저.
- **다음 단계**: `/speckit-tasks`.
