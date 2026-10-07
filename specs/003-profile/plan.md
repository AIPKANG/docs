# Implementation Plan: 프로필 수정·계정 설정

**Branch**: `003-profile` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-profile/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Tier A 공통 필수 C-AUTH-2(프로필 수정)를 구현한다. 로그인한 본인만 `/settings`에서 닉네임·소개·프로필 이미지를 [저장] 한 번에 바꾸고, 보낸 칸을 모두 검사해 하나라도 실패하면 아무것도 저장하지 않으며 실패 칸을 한 번에 돌려준다. 닉네임은 002의 `NicknamePolicy`·`NicknameChangeService`(30일 제한)를 같은 트랜잭션에서 쓰고, 소개는 0~200자·4줄·금칙어(002 `BannedWordFilter`) 규칙을 따른다. 프로필 이미지는 브라우저가 256×256 WebP로 만들어 **MinIO(사전 서명 PUT, SigV4)** 에 직접 올리고 서버가 complete에서 다시 검사한 뒤 [저장] 때 `member.profile_image_id`로 연결한다. 사진 업로드 기반(008)이 아직 없으므로 새 `media` 모듈에 `ImageStorage` 인터페이스(02 §3·04 §4-1)·S3 구현·presign/complete·정리 작업의 **프로필에 필요한 최소**를 만들고 008이 넓히도록 설계한다. 소셜 가입 때는 브라우저가 소셜 사진을 받아 같은 흐름으로 복사한다. 이메일 가입 회원은 비밀번호를 바꿀 수 있고(001 정책·세션 삭제·메일 재사용, 지금 기기는 새 세션 ID로 유지), 모든 회원은 새 글 기본 공개 범위를 정한다. 공통 ERD(V1)는 그대로 쓰고 마이그레이션을 더하지 않는다.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1, Gradle Wrapper 9.8.0 (Kotlin DSL) — 001·002와 같음

**Primary Dependencies**: Spring Web MVC, Thymeleaf, Spring Security 7, Spring Session Data Redis, Spring Data JPA, Flyway, Bean Validation(001·002 설정 재사용). **추가**: AWS SDK for Java v2 `software.amazon.awssdk:s3`(BOM 2.55.12, `S3Client`·`S3Presigner`, research R-5). 화면 JS는 의존성 없는 순수 JS(Canvas·`fetch`)

**Storage**: PostgreSQL 18(V1 `member`·`auth_identity`·`image` 그대로, 새 마이그레이션 없음) / Redis(업로드 1분 20장, 비밀번호 변경 잠금, 정리 재시도 집합, 세션) / 객체 저장소 MinIO 커뮤니티 포크 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`(로컬·테스트), 운영 NHN MinIO(설정만 다름)

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers(PostgreSQL 18·Redis·Mailpit + 저장소 `GenericContainer`). 사전 서명 주소에 실제 PUT

**Target Platform**: Linux 서버(Docker Compose), 브라우저 375px~데스크톱(Canvas WebP 인코딩 지원 브라우저, 미지원이면 PNG로 대체)

**Project Type**: web-service (모듈러 모놀리스, SSR + 설정 화면 JSON API)

**Performance Goals**: 프로필 저장 서버 처리 수십 ms(행 잠금 1 + 인덱스 조회 몇 번). 업로드 데이터는 서버를 거치지 않음(서버는 작은 JSON 두 번). 정리 작업은 부분 인덱스로 후보 조회, 한 번에 최대 500건

**Constraints**: 전부 또는 전무 저장(SC-001), 트랜잭션 안 외부 호출 금지(저장소 inspect·delete는 트랜잭션 밖), 연결된 이미지는 정리 작업이 절대 지우지 않음(SC-004), 서버는 소셜 사진 주소로 요청하지 않음(SSRF 회피), 정책 수치는 설정값, 비밀값(저장소 키)은 환경 변수

**Scale/Scope**: 회원 수천~1만. 화면 2개(설정, 소셜 사진 복사) + 기존 화면 2개 변경(소셜 가입 마무리, 블로그 상단), JSON API 6개, 표시 조각 1개 추가

미정 항목(NEEDS CLARIFICATION)은 없다. 원문에 없는 세부는 [research.md](./research.md)에서 기본값을 정했고(사용자 지시: 질문 없이 진행), 팀 확인 권장 항목은 U-1~U-8로 남겼다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 내용 | Phase 0 전 | Phase 1 후 |
|---|---|---|---|
| I. 하나의 배포 단위, 모듈러 모놀리스 | 같은 Spring Boot 앱. 프로필·비밀번호·설정은 `account`, 이미지는 새 `media` 모듈(02 §3). account → media는 공개 Service(`ProfileImageService`)만, media → account는 SPI(`ImageReferenceLookup`)만. 규칙은 Service/domain에 | PASS | PASS |
| II. 공통은 바꾸지 않고, 확장은 추가만 (NON-NEGOTIABLE) | 새 테이블·컬럼·마이그레이션 없음(V1에 필요한 컬럼·FK·인덱스가 이미 있음). 소개 200자·4줄, 256px·1MiB, 1분 20장, 24h·7d, 잠금 5회·15분, 정리 시각 모두 `blog.*` 설정값. 002 `AuthorDisplay`는 필드를 **추가**하고 기존 팩토리 유지 | PASS | PASS |
| III. 서버가 권한을 지킨다 (NON-NEGOTIABLE) | 경로에 회원 ID 없음, 대상은 `CurrentUser`만. 화면에서 숨긴 버튼(인증 전 사진, 소셜 비밀번호)도 Service가 다시 거부(403/400). 이미지 연결은 소유·용도·완료·삭제 여부를 Service가 잠금 아래 재판정. 남의 이미지 complete는 404 | PASS | PASS |
| IV. 사용자 입력은 안전하게 보여준다 | 소개는 글자만(`th:text` + `pre-line`), 보이지 않는·방향·제어 문자 제거, 자동 링크 없음. 이미지는 우리 저장소 주소만 저장. CSP·nosniff·Referrer-Policy 유지, CSP에 저장소·소셜 사진 호스트만 추가. 저장소 키는 환경 변수 | PASS | PASS |
| V. 부가 기능은 핵심을 막지 않는다 | 저장소 호출·메일·세션 삭제는 트랜잭션 밖(커밋 후). 소셜 사진 실패는 가입을 막지 않음. 정리 작업 실패는 다음 실행에서 재시도. complete 재요청은 같은 결과(멱등) | PASS | PASS |
| VI. 실제 환경으로 검증한다 | Testcontainers PostgreSQL·Redis·Mailpit + 실제 MinIO 포크. 본인만 수정·이미지 소유 검사·비밀번호 세션 처리는 통합 테스트. 수용 시나리오를 [quickstart.md](./quickstart.md) S1~S4로 옮김 | PASS | PASS |
| 기술 제약 표 | Java 21, Boot 4.1.x, Gradle, PostgreSQL, Redis, Flyway, **MinIO(S3 API) + Presigned URL(SigV4), 로컬은 커뮤니티 포크 이미지**, 메일 Mailpit/Gmail, Testcontainers | PASS | PASS |

**결과: 위반 없음.** Complexity Tracking 기재 사항 없음.

Phase 1 재확인 메모
- 002와의 정합: `NicknameChangeService.change`를 그대로 같은 트랜잭션에서 부르고, 실패를 한 번에 모으기 위한 사전 검사는 공개 메서드(`NicknamePolicy.check`, `NicknameChangeService.nextAllowedAt`)만 쓴다. 30일 제한 오류 409 계약을 유지한다(research R-2·R-4).
- 001과의 정합: `PasswordPolicy`, `SessionRevoker`(메서드 `revokeAllExcept` 추가), `LoginAttemptStore`(키를 받는 메서드로 넓힘), `MailSender`, `AccountGuard`를 재사용한다. 소셜 마무리 화면의 사진 주소 거르기는 001 컨트롤러에 정책 호출을 더하는 변경이다(research R-11).
- 04 §4-4와 다른 정리 순서(행 먼저 → 파일)를 택했다. 연결된 이미지의 파일 삭제 경합을 없애기 위함이고 재시도 요구는 지킨다(research R-9, U-4). 원칙 위반 아님.
- 008로 미룬 항목(U-8)은 같은 인터페이스·테이블에 구현을 **추가**하는 것이라 이 설계를 바꾸지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/003-profile/
├── plan.md              # 이 파일 (/speckit-plan 출력)
├── research.md          # Phase 0 출력 (R-1~R-16, U-1~U-8)
├── data-model.md        # Phase 1 출력
├── quickstart.md        # Phase 1 출력
├── contracts/           # Phase 1 출력
│   ├── web-routes.md         # 화면·JSON API·표시 조각
│   └── profile-service.md    # account·media 공개 Service, SPI, 이벤트
├── checklists/requirements.md
├── source-notes.md
├── spec.md
└── tasks.md             # Phase 2 출력 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/team/blog/
├── account/
│   ├── web/            SettingsController(/settings, /settings/social-picture),
│   │                   ProfileApiController(/api/me/profile), AccountApiController(/api/me/password, /api/me/settings),
│   │                   SocialSignupController(001, 사진 주소 거르기·완료 후 이동 변경)
│   ├── application/    ProfileService, ProfileUpdateCommand, ProfileView, BioPolicy, PasswordChangeService,
│   │                   AccountSettingsService, ProfileAvatar, ProfileImageReferences(media SPI 구현),
│   │                   PasswordChangedListener, SessionRevoker(+revokeAllExcept), AuthorDisplay·BlogOwner(+avatar)
│   ├── domain/         BioRules, SocialPictureUrlPolicy, Member(+changeBio, changeProfileImage, changeDefaultVisibility)
│   └── infra/          LoginAttemptStore(+키 지정 메서드), MemberRepository(+profileImageIds 조회)
├── media/              (새 모듈)
│   ├── web/            ImageApiController(/api/images/presign, /api/images/{id}/complete)
│   ├── application/    ImageStorage(인터페이스), ImageUploadService, ProfileImageService, ImageCleanupService,
│   │                   ImageCleanupJob, ImageReferenceLookup(SPI), ImageProperties, StorageProperties, 결과 값
│   ├── domain/         Image(엔터티), ImagePurpose, ImageStatus, ImageHeaderInspector, ImageHeader, ImageFormat
│   └── infra/          ImageRepository, S3ImageStorage, StorageConfig(S3Client·S3Presigner), StorageBucketInitializer
└── shared/
    ├── error/          ProfileValidationException·FieldError(VALIDATION_FAILED), ImageInvalidException,
    │                   InvalidProfileImageException, Password*Exception → GlobalExceptionHandler
    ├── event/          PasswordChanged
    ├── security/       ContentSecurityPolicy(설정 조립), SecurityConfig(CSP Bean 사용)
    └── config/         RequiredSecretsCheck(+저장소 키)

src/main/resources/
├── application.yml / -dev.yml / -prod.yml   blog.storage.*, blog.image.*, blog.profile.*, blog.auth.password-change.*
├── messages.properties                     003 오류 문구
├── templates/settings/{settings,social-picture}.html, templates/fragments/avatar.html,
│   templates/mail/password-changed.html, templates/blog/home.html(프로필 상단)
└── static/js/profile/{settings.js, image-cropper.js, image-upload.js, social-picture.js}

compose.yaml            storage 서비스(pgsty/silo 고정 태그, 포트 ${STORAGE_PORT:-9000})
build.gradle.kts        AWS SDK v2 BOM + s3

src/test/java/com/team/blog/
├── account/unit/       BioRulesTest, SocialPictureUrlPolicyTest, ProfileAvatarTest
├── account/integration/ ProfileUpdateIT, ProfileValidationIT, ProfileImageAttachIT, SettingsPageIT,
│                        SocialPictureIT, PasswordChangeIT, DefaultVisibilityIT, BlogHeaderProfileIT
├── media/unit/         ImageHeaderInspectorTest
├── media/integration/  ImageUploadIT, ImageCleanupIT, S3ImageStorageIT
└── support/            IntegrationTestBase(+저장소 컨테이너), TestImages, StorageTestClient
```

**Structure Decision**: 001·002와 같은 단일 Gradle 프로젝트, 02 §3의 `com.team.blog` 모듈 구조. 프로필 규칙은 `member`를 소유한 `account`에, 이미지 저장·검사·정리는 02 §3이 정한 `media`에 둔다. 008(이미지 업로드)은 `media`의 같은 클래스에 글 용도(`POST`)·썸네일·용량·`LocalImageStorage`를 더한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (Constitution Check 위반 없음).

## Phase 요약

- **Phase 0** → [research.md](./research.md): R-1~R-16 결정, 남은 확인 사항 U-1~U-8.
- **Phase 1** → [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).
- **에이전트 컨텍스트 갱신**: 이 프로젝트의 speckit-plan 스킬 정의에는 해당 단계가 없고 `CLAUDE.md`를 만들지 않기 위해 실행하지 않았다(001·002와 같음).
- **구현 순서 제안(tasks에서 확정)**: 저장소 기반(compose·SDK·`ImageStorage`·테스트 컨테이너) → US1(닉네임·소개, 저장소 없이도 동작) → US2(업로드·연결·정리) → US4(비밀번호·공개 범위) → US3(소셜 사진, US2 필요).
- **다음 단계**: `/speckit-tasks`.
