# Data Model: 프로필 수정·계정 설정 (003-profile)

**Phase 1 산출물** · 작성일 2026-10-07

이 기능은 **새 테이블·컬럼·인덱스·마이그레이션을 만들지 않는다.** 필요한 컬럼(`member.bio`·`profile_image_id`·`profile_image_url`·`default_visibility`, `image.purpose`·`status`·`detached_at`·`width`·`height`)과 FK `fk_member_profile_image`는 공통 V1 기준선(`V1__common_schema.sql`, [docs/51](../../docs/51-erd-unified.md))에 이미 있다. 결정 근거는 [research.md](./research.md).

---

## 1. PostgreSQL 엔터티 (공통 ERD, 수정 없음)

### 1.1 member — 이 기능이 쓰는 컬럼

| 컬럼 | 타입 | 제약 (51 §3) | 규칙 |
|---|---|---|---|
| nickname, nickname_changed_at | (002 소유) | `uq_member_nickname`, `ck_member_nickname` | 변경은 002 `NicknameChangeService.change`로만(같은 트랜잭션) |
| bio | varchar(200) NULL | `ck_member_bio` CHECK (bio IS NULL OR char_length(bio) <= 200) | 정리(R-3) 후 코드 포인트 0~200, 4줄 이하, 금칙어 없음. 빈 값은 NULL |
| profile_image_id | bigint NULL | `fk_member_profile_image` → image(id) ON DELETE RESTRICT | 연결된 프로필 이미지. NULL이면 기본 아이콘 |
| profile_image_url | varchar(500) NULL | — | `profile_image_id`와 **같은 트랜잭션에서 함께** 바꾸는 공개 주소 복사본(목록에서 image JOIN 회피, 11 §4-4). 소셜 주소는 들어가지 않는다 |
| default_visibility | varchar(20) NOT NULL DEFAULT 'PUBLIC' | `ck_member_default_visibility` IN ('PUBLIC','PRIVATE') | 새 글 시작 값 |
| updated_at | timestamptz | — | 프로필·설정 저장 시 갱신 |

읽기만: `handle`(읽기 전용 표시·기본 아이콘 색), `id`, `status`, `withdrawn_at`, `deleted_at`.

### 1.2 auth_identity — 읽기·비밀번호만

| 컬럼 | 쓰임 |
|---|---|
| provider | `LOCAL`만 비밀번호 변경 가능. 설정 화면의 로그인 수단 표시 |
| email | 읽기 전용 표시, 변경 알림 메일 수신 주소, 비밀번호 정책(이메일 앞부분 포함 금지) |
| password_hash | 변경 시 BCrypt 새 해시(`{bcrypt}` 접두, 001 R-4). `ck_auth_password`(LOCAL만 해시) 유지 |
| email_verified_at | 프로필 이미지 업로드 허용 여부(001 `AccountGuard.requireWritable`) |

### 1.3 image — 사진 (media 모듈 소유, 이 기능은 `purpose = 'PROFILE'` 행만 만든다)

| 컬럼 | 타입 | 이 기능의 규칙 |
|---|---|---|
| id | bigint identity | presign 응답의 `imageId` |
| uploader_id | bigint NOT NULL FK member | 현재 로그인 회원(요청 값으로 받지 않음) |
| storage_key | varchar(255) UNIQUE | `images/{yyyy}/{MM}/{uuid}.{ext}`(서버 생성, ext = webp/png/jpg/gif) |
| thumb_storage_key, thumb_size_bytes | NULL | 프로필은 썸네일 없음(FR-013) |
| original_name | varchar(255) NOT NULL | 선택한 파일 이름(없으면 `profile.webp`, 255자 자름). 경로·화면·응답에 쓰지 않음 |
| content_type | varchar(50) | `ck_image_type` 4종. presign 신고 형식 = complete 매직 바이트 형식 |
| size_bytes | integer | presign 때 신고 크기(≤ 1MiB), complete 때 실제 크기로 갱신 |
| width, height | integer NULL | **complete 통과 시에만** 256, 256. NULL이면 업로드 미완료 |
| status | varchar(20) DEFAULT 'TEMP' | `TEMP` → (프로필 연결) `ATTACHED` |
| purpose | varchar(20) DEFAULT 'POST' | 이 기능은 `PROFILE` |
| detached_at | timestamptz NULL | 교체·기본 이미지로 되돌릴 때 이전 이미지에 기록 |
| created_at | timestamptz | 앱 `Clock`으로 기록(TEMP 24시간 기준) |

인덱스: `ix_image_uploader`, `ix_image_cleanup_temp`(TEMP), `ix_image_cleanup_detached`(정리 작업).

### 1.4 상태 전이 (프로필 이미지)

```text
(presign) ──► TEMP, width NULL ──(complete 통과)──► TEMP, width=256
                 │ complete 실패: 파일·행 즉시 삭제
TEMP, width=256 ──([저장] 연결)──► ATTACHED, detached_at NULL   ← member.profile_image_id = id
ATTACHED ──(교체 또는 [기본 이미지로])──► ATTACHED, detached_at = now  ← member.profile_image_id 다른 값/NULL
정리 작업: TEMP AND created_at < now-24h  → 삭제
          detached_at < now-7d             → 삭제
          member.profile_image_id가 참조 중이면 → 건너뜀(방어)
```

연결·해제·`member` 변경은 한 트랜잭션(FR-016). 연결 대상 이미지는 `SELECT … FOR UPDATE`로 잠근다.

---

## 2. Redis 키 (001 규칙: 원문 이메일·토큰을 키에 넣지 않음)

| 키 | 값·TTL | 쓰임 |
|---|---|---|
| `img:upload:{memberId}` | 카운터, 1분 | 사진 업로드(presign) 1분 20장 (`RedisRateLimiter`) |
| `auth:pw-change-fail:{memberId}` | 연속 실패 수, TTL 15분(실패마다 연장) | 현재 비밀번호 틀림 |
| `auth:pw-change-lock:{memberId}` | `1`, TTL 15분 | 5회째 잠금 |
| `img:orphan-keys` | 집합(저장 키) | 정리 작업에서 저장소 삭제에 실패한 키 → 다음 실행에서 재시도 |
| (세션) `PENDING_PROFILE_PICTURE_URL` 세션 속성 | 거른 소셜 사진 주소 | 가입 직후 `/settings/social-picture`에서 한 번 꺼내 지움 |

---

## 3. 도메인 값 (코드, 테이블 없음)

| 값 | 필드 | 규칙 |
|---|---|---|
| `ProfileUpdateCommand` | `nickname: Field<String>`, `bio: Field<String>`, `profileImageId: Field<Long>` | `Field`는 "보냄/안 보냄 + 값(NULL 가능)". 안 보낸 칸은 바꾸지 않음 |
| `FieldError` | `field`, `code`, `message`, `nextAllowedAt?` | 오류 목록 항목(R-4) |
| `BioRules` | `normalize`, `validate` | R-3. 오류 `BIO_TOO_LONG`, `BIO_TOO_MANY_LINES`, `BIO_BANNED_WORD` |
| `ProfileAvatar` | `handle`, `nickname`, `imageUrl` | `initial()`, `colorIndex()`(0~7), `hasImage()` |
| `AuthorDisplay` (002, 확장) | + `profileImageUrl` | 목록·댓글이 같은 읽기 쿼리의 `m.profile_image_url`로 사진 표시. 기존 3인자 생성·팩토리 유지 |
| `BlogOwner` (002) | `bio`, `profileImageUrl` 포함(이미 있음) | 블로그 상단 표시 |
| `ProfileView` | `handle`, `nickname`, `bio`, `profileImageId`, `profileImageUrl`, `email`, `provider`, `emailVerified`, `defaultVisibility`, `nicknameNextAllowedAt?` | 설정 화면·`GET /api/me/profile` |
| `PasswordChangeCommand` | `currentPassword`, `newPassword`, `newPasswordConfirm` | 값은 로그·응답에 남기지 않음 |
| `SocialPictureUrlPolicy` | 허용 호스트, 크기 매개변수 | R-11 |
| `UploadTarget` | `url`, `method=PUT`, `headers{Content-Type}`, `expiresAt` | `ImageStorage.prepareUpload` |
| `StoredObject` | `size`, `contentType`, `head(≤64KiB)` | `ImageStorage.inspect` |
| `ImageHeader` | `format`, `width`, `height`, `hasMetadata` | `ImageHeaderInspector` |

### 3.1 오류 코드

| 코드 | 대상 | HTTP | 화면 문구 |
|---|---|---|---|
| `VALIDATION_FAILED` | 프로필·설정·비밀번호 규칙 | 400 (충돌만이면 409) | 입력한 내용을 확인해 주세요 (+ `errors[]`) |
| `BIO_TOO_LONG` | bio | (항목) | 소개는 200자까지 쓸 수 있어요 |
| `BIO_TOO_MANY_LINES` | bio | (항목) | 소개는 4줄까지 쓸 수 있어요 |
| `BIO_BANNED_WORD` | bio | (항목) | 사용할 수 없는 단어가 들어 있어요 |
| `INVALID_PROFILE_IMAGE` | profileImageId | (항목, 400) | 사용할 수 없는 이미지예요 |
| `INVALID_VALUE` | 모든 칸(타입 오류) | (항목) | 값을 확인해 주세요 |
| `NICKNAME_*` (002) | nickname | (항목) | 002 문구 그대로, `NICKNAME_CHANGE_TOO_SOON`은 "다음 변경 가능일: {월 일}" + `nextAllowedAt` |
| `INVALID_VISIBILITY` | defaultVisibility | (항목) | 전체 공개나 나만 보기 중에서 골라 주세요 |
| `PASSWORD_NOT_SUPPORTED` | 소셜 계정 | 400 | 소셜 로그인 계정은 비밀번호를 바꿀 수 없어요 |
| `CURRENT_PASSWORD_MISMATCH` | 현재 비밀번호 | 400 | 현재 비밀번호가 맞지 않아요 |
| `PASSWORD_SAME_AS_CURRENT` | 새 비밀번호 | 400 | 현재 비밀번호와 다른 비밀번호를 써 주세요 |
| `PASSWORD_CHANGE_LOCKED` | 5회 실패 | 429 + `Retry-After` | 비밀번호를 여러 번 틀려 15분 동안 바꿀 수 없어요 |
| `password.*` (001) | newPassword | (항목) | 001 규칙 문구 |
| `PASSWORD_MISMATCH` (001) | newPasswordConfirm | (항목) | 비밀번호와 비밀번호 확인이 달라요 |
| `IMAGE_INVALID` | presign·complete | 400 (+`detail`: `TYPE`, `SIZE`, `DIMENSION`, `CONTENT_MISMATCH`, `METADATA`, `MISSING`) | 사진을 올릴 수 없어요. 256×256 이미지만 쓸 수 있어요 등 |
| `IMAGE_PURPOSE_NOT_SUPPORTED` | presign `purpose=POST` | 400 | 지원하지 않는 사진 용도예요 (008에서 해제) |
| `RATE_LIMITED` (002) | presign 1분 20장 | 429 | 잠시 후 다시 시도해 주세요 |
| `LOGIN_REQUIRED` / `EMAIL_NOT_VERIFIED` / `ACCOUNT_WITHDRAWN` (001) | 권한 | 401 / 403 | 001 문구 |
