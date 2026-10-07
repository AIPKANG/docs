---

description: "003-profile 구현 작업 목록 (media 모듈 최소 골격 포함)"
---

# Tasks: 프로필 수정·계정 설정

**Input**: Design documents from `/specs/003-profile/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (web-routes.md, profile-service.md), quickstart.md, `.specify/memory/constitution.md`

**작업 ID 규칙**: 002는 `T001`~, 001-auth는 `T101`~, 003은 **`T201`부터** 쓴다(겹치지 않게). 001·002의 클래스(`NicknameChangeService`, `NicknamePolicy`, `BannedWordFilter`, `AccountGuard`, `PasswordPolicy`, `SessionRevoker`, `LoginAttemptStore`, `MailSender`, `RedisRateLimiter`, `GlobalExceptionHandler`, `IntegrationTestBase`)를 **확장**하고 다시 만들지 않는다.

**Tests**: 헌법 VI(권한·소유 검사 기능은 통합 테스트 필수, H2 금지). 각 스토리의 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. 통합 테스트는 Testcontainers PostgreSQL 18 + Redis + Mailpit + 저장소(`pgsty/silo` 고정 태그).

**Organization**: 사용자 스토리별로 묶는다. 스토리 순서: US1(P1) → US2(P1) → US4(P2) → US3(P2, US2 필요).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: US1~US4
- 경로는 저장소 루트 기준, 패키지 `com.team.blog`

## Path Conventions

- 메인 코드: `src/main/java/com/team/blog/`, 리소스: `src/main/resources/`
- 테스트: `src/test/java/com/team/blog/`, `src/test/resources/`

---

## Phase 1: Setup (저장소·의존성·설정)

**Purpose**: 객체 저장소(MinIO 커뮤니티 포크)와 AWS SDK v2를 프로젝트에 더한다(research R-5).

- [X] T201 `build.gradle.kts`에 AWS SDK v2 BOM(`software.amazon.awssdk:bom:2.55.12`, `platform(...)`)과 `software.amazon.awssdk:s3`를 추가한다(주석: 003 프로필 이미지, MinIO·S3 호환, SigV4)
- [X] T202 [P] `compose.yaml`에 `storage` 서비스를 추가한다: 이미지 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`(버전 고정, 04 §6-1), `command: server /data`, 환경 `MINIO_ROOT_USER: ${STORAGE_ROOT_USER:-blogminio}`, `MINIO_ROOT_PASSWORD: ${STORAGE_ROOT_PASSWORD:-blogminio-dev-secret}`, `MINIO_API_CORS_ALLOW_ORIGIN: ${APP_BASE_URL:-http://localhost:8080}`, 포트 `"${STORAGE_PORT:-9000}:9000"`만(관리 콘솔 비공개), 볼륨 `storage-data:/data`
- [X] T203 [P] `src/main/resources/application.yml`에 설정을 추가한다: `blog.storage.{endpoint: http://localhost:${STORAGE_PORT:9000}, public-base-url: ${STORAGE_PUBLIC_BASE_URL:${blog.storage.endpoint}}, bucket: blog-images, region: us-east-1, access-key: ${STORAGE_ACCESS_KEY:}, secret-key: ${STORAGE_SECRET_KEY:}, presign-ttl: 5m, create-bucket: false}`, `blog.image.{upload-per-minute: 20, profile.size: 256, profile.max-bytes: 1048576, temp-ttl: 24h, detached-ttl: 7d, cleanup.enabled: true, cleanup.cron: "0 30 4 * * *", cleanup.batch-size: 500}`, `blog.profile.{bio.max-length: 200, bio.max-lines: 4, social-picture.allowed-hosts: {GOOGLE: lh3.googleusercontent.com, GITHUB: avatars.githubusercontent.com}, social-picture.size: 256}`, `blog.auth.password-change.{max-failures: 5, lock-duration: 15m}`
- [X] T204 [P] `application-dev.yml`에 `blog.storage.{access-key: ${STORAGE_ACCESS_KEY:${STORAGE_ROOT_USER:blogminio}}, secret-key: ${STORAGE_SECRET_KEY:${STORAGE_ROOT_PASSWORD:blogminio-dev-secret}}, create-bucket: true}`, `application-prod.yml`에 `blog.storage.{endpoint: ${STORAGE_ENDPOINT:}, public-base-url: ${STORAGE_PUBLIC_BASE_URL:}, access-key: ${STORAGE_ACCESS_KEY:}, secret-key: ${STORAGE_SECRET_KEY:}, create-bucket: false}`를 넣는다
- [X] T205 [P] `src/main/java/com/team/blog/shared/config/RequiredSecretsCheck.java`의 `REQUIRED`에 `blog.storage.endpoint`, `blog.storage.public-base-url`, `blog.storage.access-key`, `blog.storage.secret-key`를 더하고 오류 문구에 `STORAGE_*` 환경 변수를 적는다. `RequiredSecretsCheckTest`가 그대로 통과하는지 확인
- [X] T206 [P] `media` 모듈 패키지 `media/{web,application,domain,infra}`의 `package-info.java`를 만든다(02 §3, 헌법 I: account 테이블을 직접 읽지 않음)

---

## Phase 2: Foundational (모든 스토리를 막는 공용 기반)

**Purpose**: 저장소 인터페이스·구현, 테스트 컨테이너, 공용 오류 응답 모양, CSP 조립.

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다.

### 테스트 기반

- [X] T207 `src/test/java/com/team/blog/support/IntegrationTestBase.java`에 저장소 컨테이너를 추가한다: `GenericContainer("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")`, `withCommand("server", "/data")`, `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` 테스트 값, 포트 9000, `Wait.forHttp("/minio/health/live")`; 동적 속성 `blog.storage.endpoint`·`public-base-url`·`access-key`·`secret-key`, `blog.storage.create-bucket=true`, `blog.image.cleanup.enabled=false`. 정적 싱글턴으로 다른 컨테이너와 함께 시작
- [X] T208 [P] `src/test/java/com/team/blog/support/TestImages.java`: `png(w, h)`·`jpeg(w, h)`(ImageIO), `jpegWithExif(w, h)`(APP1 `Exif` 조각 삽입), `webp(w, h)`(RIFF/WEBP + `VP8L` 머리말 바이트 직접 구성), `bytesOfSize(n)` 도우미
- [X] T209 [P] `src/test/java/com/team/blog/support/StorageTestClient.java`: 사전 서명 주소로 `PUT`(Java `HttpClient`, `Content-Type` 지정) 하고 상태 코드를 돌려주는 도우미 + 관리자 `S3Client`로 객체 존재 확인(`exists(key)`)
- [X] T210 [P] `src/test/java/com/team/blog/support/DatabaseCleaner.java`는 이미 `image`를 비운다 — 저장소 버킷의 테스트 객체는 매 테스트 뒤 지우지 않아도 되도록 키를 UUID로 만든다는 점만 확인(변경 없음이면 체크만)

### 저장소 (`media`, research R-5·R-6)

- [X] T211 [P] `media/application/StorageProperties.java`(`@ConfigurationProperties("blog.storage")`: endpoint, publicBaseUrl, bucket, region, accessKey, secretKey, presignTtl, createBucket)와 `media/application/ImageProperties.java`(`blog.image.*`: uploadPerMinute, profile.size, profile.maxBytes, tempTtl, detachedTtl, cleanup.enabled/cron/batchSize)
- [X] T212 [P] `media/application/ImageStorage.java` 인터페이스(04 §4-1: `UploadTarget prepareUpload(String key, String contentType, long size)`, `Optional<StoredObject> inspect(String key)`, `String publicUrl(String key)`, `void delete(String key)`)와 값 `UploadTarget(url, method, headers, expiresAt)`, `StoredObject(size, contentType, head)` — 008이 `LocalImageStorage`를 추가할 자리라고 Javadoc에 적는다
- [X] T213 `media/infra/StorageConfig.java`: `S3Client`·`S3Presigner` Bean — `endpointOverride`, `forcePathStyle(true)`, 리전, `StaticCredentialsProvider`, `requestChecksumCalculation(WHEN_REQUIRED)`·`responseChecksumValidation(WHEN_REQUIRED)`
- [X] T214 `media/infra/S3ImageStorage.java`: `prepareUpload` = `PutObjectPresignRequest`(TTL `presign-ttl`, `contentType` 서명), `inspect` = `HeadObject` + `GetObject` 범위 `bytes=0-65535`(없으면 empty), `publicUrl` = `{publicBaseUrl}/{bucket}/{key}`, `delete` = `DeleteObject`(없는 키도 성공). 예외 메시지에 키 외 비밀값을 남기지 않는다
- [X] T215 `media/infra/StorageBucketInitializer.java`: `create-bucket=true`일 때 시작 시 버킷이 없으면 만들고 정책(익명은 `arn:aws:s3:::{bucket}/images/*`에 `s3:GetObject`만, 목록 없음)을 적용(`ApplicationRunner`)
- [X] T216 `src/test/java/com/team/blog/media/integration/S3ImageStorageIT.java`: 사전 서명 PUT 200 → `inspect` 크기·앞부분 바이트 일치 → 익명 GET(공개 주소) 200 → 다른 `Content-Type` PUT 403 → `delete` 뒤 `inspect` empty

### 공용 오류·보안

- [X] T217 [P] `shared/error/FieldError.java`(field, code, message, nextAllowedAt?)와 `shared/error/ProfileValidationException.java`(오류 목록, `isConflictOnly()` = 모두 `NICKNAME_CHANGE_TOO_SOON`/동시 `NICKNAME_DUPLICATE`), `ErrorResponse`에 `errors`(List, null이면 생략)·`detail`(String, null이면 생략) 필드와 `withErrors`·`withDetail` 추가(기존 팩토리·응답 모양 유지)
- [X] T218 `shared/error/GlobalExceptionHandler.java`에 `ProfileValidationException` → `VALIDATION_FAILED` + `errors[]`(항목 문구는 `messages.properties`의 `error.{code}` 또는 `password.{code}`, `NICKNAME_CHANGE_TOO_SOON`은 날짜 인자) 400, `isConflictOnly()`면 409 매핑
- [X] T219 `shared/security/ContentSecurityPolicy.java`: `StorageProperties`와 `blog.profile.social-picture.allowed-hosts`로 CSP 문자열 조립(`img-src 'self' data: blob: {저장소 공개 출처} https://{소셜 호스트}…`, `connect-src 'self' {저장소 출처} {공개 출처} https://{소셜 호스트}…`, 나머지 지시어는 기존 그대로). `SecurityConfig`가 상수 대신 이 Bean을 쓰게 바꾸고, `SecurityHeadersIT`가 Bean 값과 비교하도록 고친다
- [X] T220 [P] `src/main/resources/messages.properties`에 003 문구 추가: `error.VALIDATION_FAILED=입력한 내용을 확인해 주세요`, `error.BIO_TOO_LONG=소개는 200자까지 쓸 수 있어요`, `error.BIO_TOO_MANY_LINES=소개는 4줄까지 쓸 수 있어요`, `error.BIO_BANNED_WORD=사용할 수 없는 단어가 들어 있어요`, `error.INVALID_PROFILE_IMAGE=사용할 수 없는 이미지예요`, `error.INVALID_VALUE=값을 확인해 주세요`, `error.INVALID_VISIBILITY=전체 공개나 나만 보기 중에서 골라 주세요`, `error.PASSWORD_NOT_SUPPORTED=소셜 로그인 계정은 비밀번호를 바꿀 수 없어요`, `error.CURRENT_PASSWORD_MISMATCH=현재 비밀번호가 맞지 않아요`, `error.PASSWORD_SAME_AS_CURRENT=현재 비밀번호와 다른 비밀번호를 써 주세요`, `error.PASSWORD_CHANGE_LOCKED=비밀번호를 여러 번 틀려 15분 동안 바꿀 수 없어요`, `error.IMAGE_INVALID=사진을 올릴 수 없어요. 256×256 크기의 jpg·png·gif·webp 사진만 쓸 수 있어요`, `error.IMAGE_PURPOSE_NOT_SUPPORTED=지원하지 않는 사진 용도예요`, `error.SOCIAL_PICTURE_FAILED=소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요`

**Checkpoint**: 저장소 PUT·조회·삭제가 실제 컨테이너로 동작하고, 기존 241개 테스트가 그대로 통과한다.

---

## Phase 3: User Story 1 - 닉네임·소개를 한 번에 저장한다 (Priority: P1) 🎯 MVP

**Goal**: 로그인한 본인이 `/settings`에서 닉네임·소개를 고쳐 [저장] — 모든 칸 검사, 하나라도 실패하면 아무것도 저장하지 않고 실패 칸을 모두 돌려준다.

**Independent Test**: 정상 저장 → `/@주소` 상단 즉시 반영; 소개 201자 + 정상 닉네임 → 닉네임도 그대로(quickstart S1).

### Tests for User Story 1 ⚠️ (먼저 작성, 실패 확인)

- [X] T221 [P] [US1] `src/test/java/com/team/blog/account/unit/BioRulesTest.java`: 200자 통과·201자 `BIO_TOO_LONG`(코드 포인트 기준, 이모지 포함), 앞뒤 공백 제거, NFD→NFC, `\r\n` 통일, 연속 빈 줄 하나로(`a\n\n\n\nb` → `a\n\nb`, 3줄), 5줄 `BIO_TOO_MANY_LINES`, 방향 문자(U+202E)·영폭 공백(U+200B)·제어 문자 제거, ZWJ 이모지 유지, 빈 값 → 빈 문자열
- [X] T222 [P] [US1] `src/test/java/com/team/blog/account/integration/ProfileUpdateIT.java`: (1) 정상 닉네임+소개 저장 200 → DB·`GET /api/me/profile` 반영, `/@주소` 상단에 새 닉네임·소개(SC-007) (2) 소개만 보냄 → 닉네임 그대로 (3) 30일 제한 중 소개만 → 저장, 같은 요청에 다른 닉네임 → 409 `VALIDATION_FAILED`(`NICKNAME_CHANGE_TOO_SOON`, `nextAllowedAt`) + 소개도 그대로 (4) 같은 닉네임 다시 보냄(제한 중) → 성공 (5) 요청 본문 `handle`·`email` 무시 (6) 비회원 401, `/settings` 비회원 303 `/login?redirect=/settings` (7) 인증 전 회원도 닉네임·소개 저장 가능(42 §9)
- [X] T223 [P] [US1] `src/test/java/com/team/blog/account/integration/ProfileValidationIT.java`: (1) 정상 닉네임 + 201자 소개 → 400, `errors`에 `bio: BIO_TOO_LONG`만, DB 닉네임·소개 모두 그대로(SC-001) (2) 형식 틀린 닉네임 + 금칙어 소개 → 두 칸 한 번에 (3) 5줄 소개 → `BIO_TOO_MANY_LINES` (4) 금칙어 소개 응답 본문에 금칙어 문자열 없음 (5) 소개 `<script>alert(1)</script>` 저장 → `/@주소` HTML에 이스케이프된 글자로(SC-002) (6) 타입 오류(`bio: 3`) → `INVALID_VALUE`
- [X] T224 [P] [US1] `src/test/java/com/team/blog/account/integration/ProfileConcurrencyIT.java`: 같은 회원 두 요청 동시 저장(다른 소개) → 둘 다 200, 최종 값은 둘 중 하나로 일관(행 잠금 직렬화), 다른 두 회원이 같은 새 닉네임 동시 저장 → 1건 성공, 나머지 409 `NICKNAME_DUPLICATE`(동시 문구)

### Implementation for User Story 1

- [X] T225 [P] [US1] `account/domain/BioRules.java`: `normalize(raw)`(research R-3 순서: 줄바꿈 통일 → 보이지 않는·방향·제어 문자 제거(`\n` 제외, `\t`→공백, ZWJ·ZWNJ·이체 선택자 유지) → NFC → 줄 끝 공백 제거 → strip → 연속 빈 줄 하나로), `codePointLength`, `lineCount`, `firstViolation(normalized, maxLength, maxLines, BannedWordFilter)` → `BIO_TOO_LONG` → `BIO_TOO_MANY_LINES` → `BIO_BANNED_WORD`
- [X] T226 [P] [US1] `account/domain/BioViolation.java` enum(`BIO_TOO_LONG`, `BIO_TOO_MANY_LINES`, `BIO_BANNED_WORD`)과 `account/application/ProfileProperties.java`(`blog.profile.*`)
- [X] T227 [US1] `account/application/BioPolicy.java`: `check(raw)` → `BioCheckResult(normalized, violation)`(설정값 200·4, `BannedWordFilter` 주입)
- [X] T228 [US1] `account/domain/Member.java`에 `changeBio(String normalizedOrNull, Instant now)`(빈 값 → NULL), `changeProfileImage(Long imageId, String url, Instant now)`, `changeDefaultVisibility(String value, Instant now)`, `touch(Instant now)` 추가(`handle` 변경 메서드는 계속 없음)
- [X] T229 [P] [US1] `account/application/ProfileUpdateCommand.java`(필드마다 `Field<T>`: present + value) 와 `account/application/ProfileView.java`(handle, nickname, bio, profileImageId, profileImageUrl, email, provider, emailVerified, defaultVisibility, nicknameNextAllowedAt)
- [X] T230 [US1] `account/application/ProfileService.java`: `view(CurrentUser)`, `update(CurrentUser, ProfileUpdateCommand)` — research R-2: `AccountGuard.requireLoggedIn` → 사전 검사(닉네임: 정리값 = 현재면 생략, 아니면 `NicknameChangeService.nextAllowedAt` → `NicknamePolicy.check(raw, memberId)`; 소개: `BioPolicy`; 이미지: US2에서 연결) → 실패 모음이면 `ProfileValidationException` → `TransactionTemplate` 한 트랜잭션: `findByIdForUpdate` → `NicknameChangeService.change`(제한·규칙 예외는 같은 오류 항목으로 변환, 롤백) → `changeBio` → `touch`. 트랜잭션 밖에서 `DataIntegrityViolationException`을 `MemberUniqueViolationTranslator.translate`로 `NICKNAME_DUPLICATE`(동시) 항목으로
- [X] T231 [US1] `account/web/ProfileApiController.java`: `GET /api/me/profile`, `PATCH /api/me/profile` — 본문을 JSON 트리로 읽어 키 존재/`null`/타입을 구분해 `ProfileUpdateCommand` 생성(타입 오류는 `INVALID_VALUE` 항목), 알 수 없는 키 무시, `CurrentUserProvider`로만 대상 결정
- [X] T232 [US1] `account/web/SettingsController.java` `GET /settings`: `requireLoggedIn`(비회원 303 로그인), `ProfileView` + `ProfileAvatar` + 닉네임 다음 변경 가능일(`KoreanDateFormatter.monthDay`)을 모델에
- [X] T233 [US1] `src/main/resources/templates/settings/settings.html`: 11 §2 화면 — 프로필(아이콘 자리, 닉네임 + 다음 변경 가능일·제한 중 비활성, 소개 textarea + `n / 200`, 블로그 주소 "변경할 수 없어요", [저장], 칸별 오류 자리 `data-error-for`), 계정(이메일·로그인 수단 읽기 전용 "변경할 수 없어요"), 회원 탈퇴 진입 링크(`/settings/withdraw`, 처리는 023), `<noscript>` 안내. 375px 가로 스크롤 없음
- [X] T234 [US1] `src/main/resources/static/js/profile/settings.js`: [저장] → 바뀐 칸만 `PATCH /api/me/profile`(CSRF 헤더) → 칸별 오류 표시/성공 표시, 소개 글자 수(코드 포인트) 표시
- [X] T235 [US1] `src/main/resources/templates/blog/home.html`과 `discovery/web/BlogPageController.java`: 블로그 상단에 프로필 아이콘(US2 조각 전에는 기본 아이콘) · `닉네임 @주소` · 소개(`th:text`, `class="profile-bio"` + `white-space: pre-line`, 자동 링크 없음)
- [X] T236 [US1] `src/main/resources/templates/layout/base.html`: 로그인 상태 머리글에 [설정](`/settings`) 링크, 소개·아바타·설정 화면 CSS(`.profile-bio`, `.avatar`, `.avatar-c0`~`.avatar-c7`, 설정 폼)

**Checkpoint**: US1 테스트 통과, 저장소 없이도 닉네임·소개 저장이 끝까지 동작.

---

## Phase 4: User Story 2 - 프로필 이미지를 올리거나 기본 이미지로 되돌린다 (Priority: P1)

**Goal**: 256×256 이미지를 저장소에 직접 올리고 [저장]으로 연결; 기본 아이콘; 정리 작업은 연결된 이미지를 지우지 않고 이전 이미지는 7일 뒤 지운다.

**Independent Test**: 업로드 → 저장 → 정리 작업 후에도 유지; 교체 → 이전 이미지 7일 뒤 삭제; 남의 이미지·글용 이미지 연결 거부(quickstart S2).

### Tests for User Story 2 ⚠️ (먼저 작성, 실패 확인)

- [ ] T237 [P] [US2] `src/test/java/com/team/blog/media/unit/ImageHeaderInspectorTest.java`: PNG·JPEG·GIF·WebP(VP8/VP8L/VP8X) 형식·크기 판별, 형식 위장(PNG 바이트를 webp로), 손상·짧은 입력 → 판별 불가, JPEG APP1 Exif·PNG eXIf·WebP EXIF/XMP 조각 → `hasMetadata`
- [ ] T238 [P] [US2] `src/test/java/com/team/blog/account/unit/ProfileAvatarTest.java`: 첫 글자(`김민서`→`김`, `kim`→`K`, 이모지 없음), 같은 주소 → 항상 같은 색 번호(0~7), 8색 모두 흰 글자와 WCAG 대비 ≥ 4.5:1(팔레트 상수 검사)
- [ ] T239 [P] [US2] `src/test/java/com/team/blog/media/integration/ImageUploadIT.java`: (1) 인증된 회원 presign(PROFILE, webp, 1MiB 이하) 200 → 실제 PUT → complete 200 `{url, 256, 256}`, 행 `TEMP`·`purpose=PROFILE`·width 256 (2) 비회원 401, 인증 전 403 `EMAIL_NOT_VERIFIED`, 탈퇴 유예 403 (3) 형식 `image/bmp`·크기 > 1MiB → 400 `IMAGE_INVALID` (4) `purpose=POST` → 400 `IMAGE_PURPOSE_NOT_SUPPORTED` (5) 1분 21번째 presign → 429 (6) complete 거부: 파일 없음, 300×300, 1MiB 초과, PNG 내용을 `image/webp`로 신고, EXIF JPEG → 400 + 저장소 파일·행 삭제 (7) 남의 이미지 complete → 404 (8) complete 두 번 → 같은 응답
- [ ] T240 [P] [US2] `src/test/java/com/team/blog/account/integration/ProfileImageAttachIT.java`: (1) 완료 이미지 `profileImageId`로 저장 → `member.profile_image_id/url` 설정, 이미지 `ATTACHED` (2) 교체 → 새 이미지 ATTACHED, 이전 `detached_at` 기록 (3) `profileImageId: null` → 기본, 이전 `detached_at` (4) 남의 이미지, `purpose=POST` 행, 미완료(width NULL) 이미지, 없는 ID, `detached_at` 있는 이미지 → `INVALID_PROFILE_IMAGE` + 프로필 그대로(SC-003) (5) 잘못된 이미지 + 정상 소개 → 소개도 그대로 (6) 인증 전 회원의 `profileImageId: null` 저장은 성공 (7) 블로그 상단·`MemberSummaryQuery`에 새 이미지 주소 반영(SC-007)
- [ ] T241 [P] [US2] `src/test/java/com/team/blog/media/integration/ImageCleanupIT.java`: (1) TEMP 24시간 지난 행 → 행·파일 삭제, 23시간 → 남음 (2) 연결된 이미지 → 여러 번 실행해도 남음(SC-004) (3) 교체된 이전 이미지 → 7일 직전 남음, 7일 뒤 첫 실행에서 삭제 (4) 참조 중(`member.profile_image_id`)인데 TEMP로 조작한 행 → 건너뜀 (5) 저장소 삭제 실패를 흉내 내면 키가 `img:orphan-keys`에 남고 다음 실행에서 지워짐

### Implementation for User Story 2

- [ ] T242 [P] [US2] `media/domain/ImagePurpose.java`(`POST`, `PROFILE`), `ImageStatus.java`(`TEMP`, `ATTACHED`), `ImageFormat.java`(JPEG/PNG/GIF/WEBP ↔ `content_type`·확장자), `media/domain/Image.java` 엔터티(`image` 14컬럼: 생성자 `profileUpload(uploaderId, storageKey, originalName, contentType, size, now)`, `complete(width, height, size)`, `isCompleted()`(width NOT NULL), `attach()`(ATTACHED, detached_at NULL), `detach(now)`)
- [ ] T243 [P] [US2] `media/domain/ImageHeaderInspector.java` + `ImageHeader.java`(format, width, height, hasMetadata): 매직 바이트, PNG IHDR, GIF 논리 화면, JPEG SOFn, WebP VP8/VP8L/VP8X 크기, 메타데이터 조각 탐지(research R-8)
- [ ] T244 [US2] `media/infra/ImageRepository.java`: `findByIdForUpdate`, `findByIdAndUploaderId`, 정리 후보 조회(`status='TEMP' AND created_at < :tempBefore` / `detached_at < :detachedBefore`, 최대 batch), 조건부 삭제(`DELETE … WHERE id = :id AND ((status='TEMP' AND created_at < :tempBefore) OR detached_at < :detachedBefore)`)
- [ ] T245 [US2] `media/application/ImageUploadService.java` + `media/web/ImageApiController.java`: `POST /api/images/presign`(`requireWritable` → `RedisRateLimiter` `img:upload:{memberId}` 1분 20장 → `purpose` PROFILE만 → 형식 4종·크기 1..1MiB → 키 `images/{yyyy}/{MM}/{uuid}.{ext}`(Clock, Asia/Seoul 아님 UTC 기준 연·월) → 행 저장 → `prepareUpload`), `POST /api/images/{id}/complete`(본인 아니면 404 → 완료면 같은 결과 → 트랜잭션 밖 `inspect` → 검사(크기 ≤ 신고·≤ 1MiB, 형식 일치, 정확히 256×256, 메타데이터 없음) → 실패: `delete` + 행 삭제 + `ImageInvalidException(detail)` → 통과: 짧은 트랜잭션으로 `complete`)
- [ ] T246 [P] [US2] `shared/error/ImageInvalidException.java`(detail: TYPE·SIZE·MISSING·CONTENT_MISMATCH·DIMENSION·METADATA), `ImagePurposeNotSupportedException.java`, `InvalidProfileImageException.java` + `GlobalExceptionHandler` 매핑(400 + `detail`)
- [ ] T247 [US2] `media/application/ProfileImageService.java`(`@Transactional(propagation = REQUIRED)`): `isValidCandidate(memberId, imageId)`, `attach(memberId, imageId)`(행 잠금 + 재판정 → ATTACHED → 공개 주소, 실패 `InvalidProfileImageException`), `detach(imageId)`(Clock)
- [ ] T248 [US2] `ProfileService`에 이미지 칸 연결: 사전 검사 `isValidCandidate` → `INVALID_PROFILE_IMAGE` 항목; 트랜잭션 안에서 같은 ID면 생략, 아니면 이전 이미지 `detach` + 새 이미지 `attach` + `member.changeProfileImage(id, url)`; `null`이면 이전 `detach` + `changeProfileImage(null, null)`(FR-016 한 트랜잭션)
- [ ] T249 [US2] `media/application/ImageReferenceLookup.java`(SPI `Set<Long> referencedIds(Collection<Long>)`)와 account 구현 `account/application/ProfileImageReferences.java`(`MemberRepository`에 `select m.profileImageId from Member m where m.profileImageId in :ids` 추가)
- [ ] T250 [US2] `media/application/ImageCleanupService.java`(`runOnce()`: `img:orphan-keys` 재시도 → 후보 조회 → 참조 중 제외 → 행 조건부 삭제(건마다 짧은 트랜잭션) → 커밋 후 저장소 삭제(원본·썸네일 키), 실패 키 `SADD`) + `media/application/ImageCleanupJob.java`(`@Scheduled(cron = "${blog.image.cleanup.cron}", zone = "Asia/Seoul")`, `@ConditionalOnProperty(blog.image.cleanup.enabled)`), `BlogApplication`/설정에 `@EnableScheduling`
- [ ] T251 [P] [US2] `account/application/ProfileAvatar.java`(handle, nickname, imageUrl; `initial()`, `colorIndex()` = `Math.floorMod(handle.hashCode(), 8)`, `hasImage()`, 팔레트 상수 8색)와 `templates/fragments/avatar.html`(`avatar(avatar, size)`: 이미지 `<img alt="">` / 기본 `<span class="avatar avatar-c{n}" aria-hidden="true">`)
- [ ] T252 [US2] `AuthorDisplay`에 `profileImageUrl` 필드 추가(기존 3인자 생성자·`of(handle, nickname, withdrawnAt)` 유지, 4인자 `of` 추가, `avatar()` 값), `MemberSummaryQuery`·`BlogOwner.toAuthorDisplay()`가 사진 주소를 채우고, `fragments/author.html`에 `avatar(author, size)` 조각 추가(탈퇴면 회색 기본)
- [ ] T253 [US2] 블로그 상단(`blog/home.html`)과 설정 화면에 아바타 조각 사용, 설정 화면 [이미지 변경](인증 전이면 숨기고 "이메일 인증 후 사진을 올릴 수 있어요") · [기본 이미지로] · 자르기 대화 상자 마크업
- [ ] T254 [US2] `static/js/profile/image-cropper.js`(파일 형식·10MB 화면 검사, 끌어서 위치·확대 슬라이더, 정사각형 틀, 캔버스 256×256 `toBlob('image/webp', 0.85)`, WebP 미지원이면 PNG) + `static/js/profile/image-upload.js`(presign → PUT(`Content-Type`) → complete, 오류 문구) + `settings.js`에 연결(미리보기, [저장] 때 `profileImageId`, [기본 이미지로] → `null`)

**Checkpoint**: US1·US2 테스트 통과. 연결된 이미지가 정리 작업에서 살아남는다.

---

## Phase 5: User Story 4 - 비밀번호를 바꾸고 새 글 기본 공개 범위를 정한다 (Priority: P2)

**Goal**: 이메일 가입 회원의 비밀번호 변경(다른 기기 로그아웃·지금 기기 유지·알림 메일·5회 잠금), 모든 회원의 기본 공개 범위.

**Independent Test**: 두 기기 로그인 → 한쪽 변경 → 다른 쪽 로그아웃·현재 유지·메일; 소셜 계정 거부(quickstart S4).

### Tests for User Story 4 ⚠️ (먼저 작성, 실패 확인)

- [ ] T255 [P] [US4] `src/test/java/com/team/blog/account/integration/PasswordChangeIT.java`(`Browser` A·B로 실제 로그인): (1) A에서 변경 204 → A 세션 쿠키 값이 바뀌고 A의 다음 요청 로그인 유지, B는 로그인 필요, 새 비밀번호로 로그인 성공·옛 비밀번호 실패, Mailpit에 "비밀번호가 변경됐어요" 메일(재설정 링크 `/password/forgot`) (2) 현재 비밀번호 틀림 → 400 `CURRENT_PASSWORD_MISMATCH`, 5회 → 6번째는 맞는 비밀번호여도 429 `PASSWORD_CHANGE_LOCKED` + `Retry-After`, 15분 뒤(Redis TTL) 해제 (3) 새 = 현재 → `PASSWORD_SAME_AS_CURRENT` (4) 정책 위반(`short1!`)·확인 불일치 → 400 `VALIDATION_FAILED`(`newPassword`, `newPasswordConfirm`) (5) 소셜 계정 → 400 `PASSWORD_NOT_SUPPORTED` (6) 비회원 401
- [ ] T256 [P] [US4] `src/test/java/com/team/blog/account/integration/DefaultVisibilityIT.java`: `PRIVATE`로 변경 → 200, `AccountSettingsService.defaultVisibility` = `PRIVATE`, `GET /api/me/profile` 반영; `FRIENDS`·`ALL`·없음 → 400 `INVALID_VISIBILITY`; 비회원 401
- [ ] T257 [P] [US4] `src/test/java/com/team/blog/account/integration/SettingsPageIT.java`: LOCAL 회원 화면에 [비밀번호 변경] 폼, 소셜 회원 화면에는 없음; 이메일·주소 읽기 전용 "변경할 수 없어요"; 기본 공개 범위 라디오 현재 값 선택; 탈퇴 진입 링크; 30일 제한 중 닉네임 비활성 + 다음 변경 가능일; 인증 전 회원은 [이미지 변경] 대신 안내

### Implementation for User Story 4

- [ ] T258 [P] [US4] `account/infra/LoginAttemptStore.java`에 키를 받는 `isLocked(String lockKey)`, `recordFailure(String failKey, String lockKey, int max, Duration lock)`, `clear(String failKey)`, `lockTtlSeconds(lockKey)` 추가(기존 이메일 메서드는 이 메서드에 위임), 비밀번호 변경 키 `auth:pw-change-fail:{memberId}`, `auth:pw-change-lock:{memberId}`
- [ ] T259 [P] [US4] `account/application/SessionRevoker.java`에 `revokeAllExcept(long memberId, String keepSessionId)` 추가
- [ ] T260 [P] [US4] `shared/event/PasswordChanged.java`(memberId, email, keepSessionId; `toString` 마스킹) + `shared/error/PasswordNotSupportedException`, `CurrentPasswordMismatchException`, `PasswordSameAsCurrentException`, `PasswordChangeLockedException(retryAfterSeconds)` + `GlobalExceptionHandler` 매핑(400·400·400·429+`Retry-After`)
- [ ] T261 [US4] `account/application/PasswordChangeService.java`: research R-13 순서(LOCAL 아님 → 잠금 → BCrypt 비교 실패(+1, 5회째 잠금) → 같음 → `PasswordPolicy.validate(new, email)`·확인 → `ProfileValidationException`) → `changePasswordHash` → 실패 수 삭제 → `PasswordChanged` 발행; `blog.auth.password-change.*` 설정(`AuthProperties`에 추가)
- [ ] T262 [US4] `account/application/PasswordChangedListener.java`(`@TransactionalEventListener(AFTER_COMMIT)`: `revokeAllExcept`, 메일 `mail/password-changed` 제목 "[블로그] 비밀번호가 변경됐어요", 링크 `{link-base-url}/password/forgot`; 서로 실패 격리, 마스킹 로그) + `templates/mail/password-changed.html`
- [ ] T263 [US4] `account/web/AccountApiController.java`: `POST /api/me/password`(지금 세션 ID를 넘겨 서비스 호출 → 성공 뒤 `request.changeSessionId()` → 204), `PATCH /api/me/settings`
- [ ] T264 [US4] `account/application/AccountSettingsService.java`: `defaultVisibility(memberId)`, `changeDefaultVisibility(CurrentUser, value)`(`PUBLIC`/`PRIVATE`만, 그 밖 `INVALID_VISIBILITY` — 친구 공개는 추가 구현자가 값 추가)
- [ ] T265 [US4] 설정 화면에 [비밀번호 변경] 폼(LOCAL만, 현재·새·확인, 001 `password-rules.js` 규칙 표시 재사용)과 기본 공개 범위 라디오 + `settings.js`에 두 API 연결(오류 칸 표시, 변경 성공 안내)

**Checkpoint**: US1·US2·US4 독립 동작.

---

## Phase 6: User Story 3 - 소셜 가입 때 소셜 프로필 사진을 가져온다 (Priority: P2)

**Goal**: 소셜 가입 마무리에서 "프로필 사진 사용"(기본 체크) → 계정 생성 직후 브라우저가 사진을 복사해 연결. 서버는 소셜 주소로 요청하지 않고 저장하지 않는다.

**Independent Test**: 사진 사용 켜고 소셜 가입 → 프로필 주소가 우리 저장소; 실패 흉내 → 기본 이미지 + 안내(quickstart S3).

### Tests for User Story 3 ⚠️ (먼저 작성, 실패 확인)

- [ ] T266 [P] [US3] `src/test/java/com/team/blog/account/unit/SocialPictureUrlPolicyTest.java`: Google `…/a/abc=s96-c` → `…=s256-c`, 크기 매개변수 없으면 `=s256-c` 추가, GitHub `…/u/1?v=4` → `…?v=4&s=256`, 기존 `s` 교체; `http://`, 다른 호스트(`lh3.googleusercontent.com.evil.com`, `evil.com`), userinfo, 다른 포트, 공급자-호스트 불일치 → empty
- [ ] T267 [P] [US3] `src/test/java/com/team/blog/account/integration/SocialPictureIT.java`: (1) 허용 호스트 사진의 Google 대기 정보 → `/signup/social` 화면에 `=s256-c` 주소·체크 상자(기본 체크) (2) 허용 밖 주소 → 화면에 주소·체크 상자 없음 (3) 체크한 채 완료 → 303 `/settings/social-picture`, 그 화면 `data-picture-url` = 거른 주소, 두 번째 열기 → 303 `/`(한 번만) (4) 체크 해제 → 303 `/`, 기본 아이콘 (5) 인증된 이메일 없는 GitHub 가입 → 303 `/` (6) 브라우저 흐름 흉내(presign → PUT → complete → PATCH) 뒤 `member.profile_image_url`이 저장소 공개 주소로 시작하고 소셜 호스트를 포함하지 않음(SC-005), DB 어디에도 소셜 주소 없음

### Implementation for User Story 3

- [ ] T268 [P] [US3] `account/domain/SocialPictureUrlPolicy.java`(`sanitize(Provider, String) → Optional<String>`, 허용 호스트·크기 설정 주입은 `ProfileProperties`) 
- [ ] T269 [US3] `account/web/SocialSignupController.java`(001): `page()`의 `pictureUrl`을 정책 통과 값으로, 완료 후 "사진 사용" + 거른 주소 + `pending.hasVerifiedEmail()`이면 세션 `PENDING_PROFILE_PICTURE_URL`에 넣고 303 `/settings/social-picture`, 아니면 `/`. `social-signup.html` 체크 상자 옆 미리보기 `<img alt="" width=48 height=48>`
- [ ] T270 [US3] `SettingsController` `GET /settings/social-picture`(로그인 필요, 세션 값을 꺼내 지움, 없으면 303 `/`) + `templates/settings/social-picture.html`(진행 표시, 실패 안내 "소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요" + [계속하기] `/`·[설정으로] `/settings`, `<noscript>`에 같은 안내)
- [ ] T271 [US3] `static/js/profile/social-picture.js`: `fetch(url, {mode:'cors', credentials:'omit', referrerPolicy:'no-referrer'})` 5초 `AbortController` → `createImageBitmap` → 가운데 정사각형 → 256×256 WebP 0.85 → `image-upload.js` 흐름 → `PATCH /api/me/profile {profileImageId}` → 성공 `/`, 실패 안내 표시

**Checkpoint**: 모든 스토리 독립 동작.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T272 [P] `SecretLeakIT`(001)에 저장소 비밀값(`blog.storage.secret-key`)이 로그·오류 응답에 나오지 않는지 항목 추가, presign 응답에 루트 키가 없는지 확인(서명 매개변수만)
- [ ] T273 [P] `README.md`의 실행 안내에 `storage` 서비스·`STORAGE_PORT`·`STORAGE_*` 환경 변수 한 줄씩 추가(docs/는 고치지 않음)
- [ ] T274 quickstart 확인: compose(바꾼 포트) + jar(dev)로 S1·S2·S4를 curl로, 설정 화면 JS(자르기·업로드·저장, 비밀번호 폼)를 내장 브라우저로 확인. 확인 못 한 항목(실제 Google·GitHub 사진, CORS 실제 소셜 호스트)은 research 구현 메모에 기록
- [ ] T275 `./gradlew clean build` 전체 통과 확인(기존 241개 + 003 테스트), 테스트 수를 research 구현 메모에 기록
- [ ] T276 `specs/003-profile/research.md`에 "구현 메모 (/speckit-implement)" 표(I-1~)로 tasks와 달라진 점·추가 결정 기록

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T201(의존성)이 T213·T214의 전제
- **Foundational (Phase 2)**: Setup 뒤. 모든 스토리를 막는다. T207(테스트 컨테이너)은 T216과 모든 통합 테스트의 전제
- **US1 (Phase 3)**: Foundational 뒤. 저장소 없이도 동작(MVP)
- **US2 (Phase 4)**: Foundational 뒤(T248은 T230 이후). US1과 병렬 가능하나 `ProfileService`를 함께 고치므로 US1 다음 권장
- **US4 (Phase 5)**: Foundational 뒤. US1·US2와 독립(T265는 설정 화면 T233 이후)
- **US3 (Phase 6)**: US2(업로드·연결) 필요
- **Polish (Phase 7)**: 모든 스토리 뒤

### Within Each User Story

- 테스트 먼저(실패 확인) → 도메인·값 → Service → 컨트롤러 → 화면·JS
- 같은 파일(`ProfileService`, `GlobalExceptionHandler`, `settings.html`, `settings.js`)을 고치는 작업은 순서대로

### Parallel Opportunities

- Phase 1: T202~T206 병렬
- Phase 2: T208·T209·T211·T212·T217·T220 병렬
- US1 테스트 T221~T224 병렬, T225·T226·T229 병렬
- US2 테스트 T237~T241 병렬, T242·T243·T246·T251 병렬
- US4 테스트 T255~T257 병렬, T258·T259·T260 병렬
- US3 T266·T267·T268 병렬

---

## Parallel Example: User Story 2

```text
Task: "T237 ImageHeaderInspectorTest in src/test/java/com/team/blog/media/unit/ImageHeaderInspectorTest.java"
Task: "T239 ImageUploadIT in src/test/java/com/team/blog/media/integration/ImageUploadIT.java"
Task: "T241 ImageCleanupIT in src/test/java/com/team/blog/media/integration/ImageCleanupIT.java"
Task: "T242 Image 엔터티 in src/main/java/com/team/blog/media/domain/Image.java"
Task: "T243 ImageHeaderInspector in src/main/java/com/team/blog/media/domain/ImageHeaderInspector.java"
```

## Parallel Example: User Story 4

```text
Task: "T258 LoginAttemptStore 키 지정 메서드 in src/main/java/com/team/blog/account/infra/LoginAttemptStore.java"
Task: "T259 SessionRevoker.revokeAllExcept in src/main/java/com/team/blog/account/application/SessionRevoker.java"
Task: "T260 PasswordChanged 이벤트·예외 in src/main/java/com/team/blog/shared/event/PasswordChanged.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1·2(저장소 기반은 US2부터 쓰이지만 테스트 기반이 함께 바뀌므로 먼저)
2. Phase 3(US1) → 닉네임·소개 저장 독립 검증 → 커밋

### Incremental Delivery

1. Setup + Foundational → 커밋
2. US1 → 테스트 → 커밋 (MVP)
3. US2 → 테스트 → 커밋
4. US4 → 테스트 → 커밋
5. US3 → 테스트 → 커밋
6. Polish → `./gradlew clean build` → 커밋

## Notes

- 각 Phase 끝에 한국어 커밋 메시지로 로컬 커밋(푸시하지 않음), 비밀값 커밋 금지(로컬 기본 저장소 계정은 개발 전용 기본값만)
- `docs/`는 고치지 않는다. 원문과 다른 결정은 research.md에 남긴다
