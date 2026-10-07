# Research: 프로필 수정·계정 설정 (003-profile)

**Phase 0 산출물** · 작성일 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `.specify/memory/constitution.md`, `docs/11-profile.md`, `docs/01-common-requirements.md` §4(결정 기록, 우선), `docs/42-permission-matrix.md` §9·§10, `docs/51-erd-unified.md`, `docs/04-draft-and-image.md` §4·§6-1, `docs/23-image.md`, `docs/02-architecture.md` §3, [008 image-upload spec](../008-image-upload/spec.md), [001 research](../001-auth/research.md)(I-1~I-16 포함), [002 research](../002-blog-address-nickname/research.md)(I-1~I-10 포함), 현재 `src/` 코드

각 항목은 **Decision / Rationale / Alternatives considered** 형식이다. 001·002에서 이미 정한 기술 결정(001 R-1 SSR·세션, R-2 Boot 4.1.1, R-3 세션 인덱스 저장소, R-4 비밀번호 정책, R-6 Redis 요청 제한, R-13 메일, R-15 테스트, R-16 Gradle / 002 R-2 공개 Service, R-7 경합 번역, R-9 금칙어 필터, R-12 닉네임 30일)은 그대로 이어받고 다시 적지 않는다. 사용자 지시(2026-10-07): 질문 없이 합리적인 기본값을 정하고 여기에 남긴다.

---

## R-1. 기술 기준과 모듈 배치

- **Decision**: Java 21, Spring Boot 4.1.1, Gradle 9.8.0, Thymeleaf SSR + Spring Session Redis, PostgreSQL 18 + Flyway, Testcontainers(001·002와 같음). 프로필·비밀번호 변경·계정 설정은 `member`/`auth_identity`를 소유한 **`account` 모듈**에, 이미지 업로드·검사·정리는 새 **`media` 모듈**(02 §3의 `media/`)에 둔다. account는 media의 공개 Service(`ProfileImageService`)만 부르고, media는 account 테이블을 읽지 않고 SPI(`ImageReferenceLookup`)로만 "회원 프로필이 참조 중인 이미지"를 묻는다.
- **Rationale**: 헌법 I(모듈은 공개 Service로만 소통). 02 §3이 `media/`에 `ImageService(presign·complete)`, `ImageCleanupJob`, `ImageStorage` 인터페이스를 두도록 정했으므로 008이 같은 자리를 넓히면 된다.
- **Alternatives considered**: 프로필 이미지를 account 안에서 처리 — 008이 같은 `image` 테이블·저장소를 다시 만들게 되어 규칙이 갈라진다.

## R-2. 프로필 저장: 검사 모으기와 한 트랜잭션 (FR-004~FR-008, SC-001)

- **Decision**: `account.application.ProfileService.update(CurrentUser, ProfileUpdateCommand)`.
  1. **사전 검사(모든 칸, 쓰기 없음)**: 보낸 칸만 검사하고 실패를 모두 모은다.
     - 닉네임: 정리값이 지금 닉네임과 글자까지 같으면 "변경 아님"(검사 생략). 아니면 `NicknameChangeService.nextAllowedAt(memberId)`로 30일 제한 → `NICKNAME_CHANGE_TOO_SOON`, 이어서 `NicknamePolicy.check(raw, memberId)` → 09 다섯 코드 중 첫 실패.
     - 소개: `BioRules`(R-3).
     - 이미지: `profileImageId`가 숫자면 `ProfileImageService.checkCandidate(memberId, imageId)` → `INVALID_PROFILE_IMAGE`. `null`이면 기본 이미지로(검사 없음).
  2. 실패가 하나라도 있으면 `ProfileValidationException(errors)` — **아무것도 쓰지 않는다**.
  3. 통과하면 한 트랜잭션(`@Transactional`)에서: `member` 행 잠금(`findByIdForUpdate`) → 닉네임이 바뀌면 `NicknameChangeService.change`(같은 트랜잭션 `REQUIRED`, 제한·규칙을 잠금 아래에서 다시 검사) → 소개 저장 → 이미지 연결/해제(`ProfileImageService.attach`/`detach`, 같은 트랜잭션, R-9) → `updated_at`.
  4. 트랜잭션 안의 재검사 실패(다른 요청이 그사이 바꾼 경우)도 같은 오류 응답으로 바꾸고 전체 롤백. DB `uq_member_nickname` 경합은 트랜잭션 **바깥**의 파사드에서 `MemberUniqueViolationTranslator.translate`로 `NICKNAME_DUPLICATE`(동시) 오류 항목으로 바꾼다(002 R-7).
  - 동시 수정은 행 잠금으로 직렬화되며 나중에 커밋한 쪽 값이 남는다(FR-007, 버전 비교 없음).
  - 보낸 칸만 바꾼다: 본문에 키가 없으면 그 칸은 그대로. `"profileImageId": null`은 기본 이미지로, `"bio": null`은 빈 소개로, `"nickname": null`은 "보내지 않음"으로 본다.
- **Rationale**: 002 `NicknameChangeService`는 첫 실패에서 예외를 던져 FR-005("실패한 칸을 모두 한 번에")를 혼자서는 만족하지 못한다. 공개 메서드(`check`, `nextAllowedAt`)로 먼저 모으고, 쓰기는 기존 `change`를 그대로 불러 규칙을 두 곳에 두지 않는다. 행 잠금 아래 재검사는 사전 검사와 커밋 사이의 경합을 막는다.
- **Alternatives considered**: 칸마다 독립 저장 — FR-005 위반. `NicknameChangeService`에 "검사만" 모드 추가 — 기존 계약을 바꾸지 않고도 공개 메서드로 충분하다.

## R-3. 소개 규칙 (FR-009~FR-011, SC-002)

- **Decision**: `account.domain.BioRules` 순수 함수 + `BioPolicy`(금칙어 필터 주입).
  1. 정리: 줄바꿈 통일(`\r\n`·`\r` → `\n`) → **보이지 않는 문자·방향 문자·제어 문자 제거**(헌법 IV): `\n`을 뺀 C0/C1 제어 문자, `\t`는 공백으로, U+200B·U+2060·U+FEFF·U+00AD, 방향 문자 U+200E·U+200F·U+061C·U+202A~U+202E·U+2066~U+2069. 이모지 결합에 필요한 U+200D(ZWJ)·U+200C·이체 선택자는 **남긴다** → NFC → 각 줄 끝 공백 제거 → 앞뒤 공백 제거 → **연속된 빈 줄을 하나로**.
  2. 길이: 정리값의 **코드 포인트 수** ≤ 200 → 아니면 `BIO_TOO_LONG`. (DB `ck_member_bio`의 `char_length`와 같은 단위. 👨‍👩‍👧 같은 결합 이모지는 여러 글자로 센다.)
  3. 줄 수: `\n` 기준 줄 수 ≤ 4 → 아니면 `BIO_TOO_MANY_LINES`.
  4. 금칙어: `BannedWordFilter.containsBanned(정리값)` → `BIO_BANNED_WORD`(예약어 검사 없음). 걸린 단어는 응답·로그에 넣지 않는다.
  - 검사 순서는 길이 → 줄 수 → 금칙어, 소개 칸 오류는 첫 실패 하나만 돌려준다. 정리 결과가 빈 문자열이면 `bio = NULL`로 저장.
  - 표시: `th:text`(이스케이프) + CSS `white-space: pre-line`(줄바꿈만 살림). 자동 링크 없음(FR-010).
- **Rationale**: 11 §3 표 그대로. 코드 포인트로 세면 앱 검사와 DB CHECK가 어긋나지 않는다(그래핌으로 세면 DB가 거부하는 값이 통과할 수 있다). ZWJ를 지우면 가족·직업 이모지가 깨진다.
- **알려진 한계**: 소개는 공백이 허용되어 `시 발`처럼 띄어 쓴 우회는 닉네임과 달리 형식 규칙으로 막히지 않는다. "닉네임과 같은 필터"(FR-011) 그대로 두고 공백 제거 변형은 넣지 않았다(단어 경계를 넘는 오탐 위험). 팀 확인 U-3.
- **Alternatives considered**: 그래핌 단위 길이 — DB와 불일치. 금칙어 단어를 `*`로 가리고 저장 — 11 §3은 거부로 정했다.

## R-4. 오류 응답 모양 (FR-005, 11 §5)

- **Decision**: 프로필·설정·비밀번호 API의 검사 실패는 `{ "code": "VALIDATION_FAILED", "message": "입력한 내용을 확인해 주세요", "errors": [ { "field", "code", "message", "nextAllowedAt"? } ] }`. HTTP는 **400**, 단 오류가 모두 "충돌" 종류(`NICKNAME_CHANGE_TOO_SOON`, 동시 경합 `NICKNAME_DUPLICATE`)뿐이면 **409**(11 §5의 `409 NICKNAME_CHANGE_TOO_SOON`과 002 계약 유지). 프로필 이미지 오류도 같은 목록의 `profileImageId` 항목(`INVALID_PROFILE_IMAGE`)으로 나간다(11 §5 "400 `INVALID_PROFILE_IMAGE`"와 HTTP 코드 같음). 단일 이유 오류(`PASSWORD_NOT_SUPPORTED`, `CURRENT_PASSWORD_MISMATCH`, `PASSWORD_SAME_AS_CURRENT`, `PASSWORD_CHANGE_LOCKED`, 이미지 업로드 오류)는 기존 `ErrorResponse { code, message }`.
- **Rationale**: 11 §5 예시 JSON 그대로이며, 화면이 칸별 문구를 그대로 그릴 수 있다. `ErrorResponse`에 `errors` 필드를 null일 때 생략하도록 추가하면 기존 응답은 바뀌지 않는다.
- **Alternatives considered**: 칸마다 다른 HTTP 코드 — 한 응답에 한 코드만 가능.

## R-5. 파일 저장소: MinIO(커뮤니티 포크) + AWS SDK v2 (결정 기록 2026-10-06)

- **Decision**:
  - 로컬·테스트 이미지: **`pgsty/silo:RELEASE.2026-09-16T00-00-00Z`**(04 §6-1이 정한 MinIO 커뮤니티 포크, 버전 고정, `server /data`, 환경 변수 `MINIO_*`). 2026-10-07 이 기계(Colima)에서 받아 기동·`/minio/health/live` 200을 확인했다. `compose.yaml`에 `storage` 서비스로 추가하고 호스트 포트는 `${STORAGE_PORT:-9000}`(002 I-6처럼 바꿀 수 있게), 루트 계정은 `${STORAGE_ROOT_USER:-blogminio}`/`${STORAGE_ROOT_PASSWORD:-blogminio-dev-secret}`(로컬 전용 기본값, 운영 비밀 아님), CORS `MINIO_API_CORS_ALLOW_ORIGIN=${APP_BASE_URL:-http://localhost:8080}`. 관리 콘솔 포트는 열지 않는다.
  - 앱: **AWS SDK for Java v2 `software.amazon.awssdk:s3` (BOM 2.55.12, 2026-10-07 Maven Central 최신)** — `S3Client` + `S3Presigner`, `endpointOverride`, `forcePathStyle(true)`, 리전 `us-east-1`(설정값), 서명 SigV4(Presigner 기본). SDK의 기본 요청 체크섬 계산은 `WHEN_REQUIRED`로 둔다(사전 서명 PUT에 체크섬 헤더가 서명되어 브라우저 업로드가 깨지는 일을 막음).
  - 설정 `blog.storage.*`: `endpoint`, `public-base-url`(사진을 내려줄 주소, 기본 = endpoint), `bucket`(기본 `blog-images`), `region`, `access-key`/`secret-key`(환경 변수 `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY`), `presign-ttl=5m`, `create-bucket`(개발·테스트만 `true`: 시작할 때 버킷이 없으면 만들고 정책 "익명은 `images/*` `GetObject`만" 적용). 운영(`prod`)은 `create-bucket=false`, 키·주소가 비면 `RequiredSecretsCheck`가 기동을 멈춘다.
  - 공개 주소: `{public-base-url}/{bucket}/{storage_key}`(path-style). CSP `img-src`·`connect-src`에 저장소 출처를 더한다(R-12).
- **Rationale**: 결정 기록(01 §4, 2026-10-06)이 MinIO + SigV4 Presigned URL을 확정했고, 04 §6-1이 로컬 이미지와 SDK 설정(`endpointOverride`+`forcePathStyle`)을 정했다. 23의 RustFS 제안은 결정 기록(10-06)으로 대체되었다(헌법 개발 흐름 4: 결정 기록 우선).
- **008로 미루는 것(이 기능은 프로필에 필요한 최소만)**: 앱 전용 키 발급 스크립트(`mc`), 익명 목록 금지 등 23 §2-3 검증 7가지 자동 테스트, `LocalImageStorage`(저장소 없는 환경용), 사용량 1GB·하루 200장, 글 사진 원본·썸네일, GIF 프레임 검사, 대체글. 이 기능은 같은 인터페이스·테이블·키 규칙을 쓰므로 008은 구현을 **추가**만 하면 된다.
- **Alternatives considered**: Testcontainers `MinIOContainer` 모듈 — 공식 `minio/minio` 이미지 이름을 전제로 해서 포크 이미지에 호환 선언이 필요하고 의존성이 늘어난다. `GenericContainer`로 충분하다. MinIO Java SDK — 04 §4-1이 AWS SDK v2로 정했다(다른 S3 호환 저장소로 옮길 때 설정만 바꿈).

## R-6. `ImageStorage` 인터페이스 (02 §3, 04 §4-1)

- **Decision**: `media.application.ImageStorage`(04 §4-1 시그니처 그대로, 값 타입만 구체화):
  ```text
  UploadTarget prepareUpload(String key, String contentType, long size)   // PUT 주소·필수 헤더·만료 시각
  Optional<StoredObject> inspect(String key)                              // 존재·실제 크기·Content-Type·앞부분 바이트(최대 64KiB)
  String publicUrl(String key)
  void delete(String key)                                                 // 없는 키도 성공
  ```
  구현 `media.infra.S3ImageStorage`(위 R-5). 시작 시 버킷 준비는 `StorageBucketInitializer`(설정 `create-bucket`). 008은 `LocalImageStorage`를 같은 인터페이스로 추가하고 `blog.storage.type`으로 고른다.
- **Rationale**: 02 §7 "이미지 S3 → `ImageStorage` 구현체 교체(설정)". 업로드 데이터는 서버를 거치지 않는다(004 §4-1 ④).
- **Alternatives considered**: 저장소 호출을 Service에 직접 — 트랜잭션 경계 관리와 테스트 대역이 어려워진다.

## R-7. 프로필 이미지 업로드 흐름 (FR-012, FR-013, FR-017)

- **Decision**:
  1. 브라우저: 파일 선택(jpg·png·gif·webp, 10MB 이하, 그 밖이면 화면에서 거부) → 자르기 화면(끌어서 위치, 확대·축소 슬라이더, 정사각형 틀) → 캔버스로 **256×256 WebP 품질 0.85**(`canvas.toBlob('image/webp', 0.85)`). 캔버스로 다시 그리므로 EXIF가 남지 않고, GIF·움직이는 WebP는 첫 장면만 그려진다. 브라우저가 WebP 인코딩을 못 하면(`toBlob` 결과 형식이 다름) PNG로 보낸다(서버는 4형식 허용).
  2. `POST /api/images/presign { "purpose": "PROFILE", "contentType": "image/webp", "size": 18234, "originalName": "me.jpg" }` → 서버: `AccountGuard.requireWritable`(비회원 401, 탈퇴 유예·인증 전 403) → 요청 제한(사용자당 1분 20장, Redis `img:upload:{memberId}`) → 형식(4종)·크기(프로필 최대 1MiB) 검사 → `image` 행(`status=TEMP`, `purpose=PROFILE`, `storage_key=images/{yyyy}/{MM}/{uuid}.{ext}`, `size_bytes`=신고 크기, `original_name`=파일 이름(경로·화면에 쓰지 않음, 없으면 `profile.webp`, 255자로 자름)) → 5분 유효 PUT 주소.
  3. 브라우저 → 저장소 PUT(`Content-Type` 헤더 일치 필수).
  4. `POST /api/images/{id}/complete` → 서버: 본인 이미지 아니면 404(존재 비노출) → 이미 완료면 같은 결과(멱등) → `inspect`: 없음, 실제 크기 > 신고 크기 또는 > 1MiB, 매직 바이트 형식 ≠ 신고 형식, **정확히 256×256 아님**, EXIF/메타데이터 조각 있음 → 거부(`IMAGE_INVALID` + 이유 `detail`) 후 저장소 파일 삭제·행 삭제. 통과하면 `width`·`height`·실제 `size_bytes` 기록, `{ imageId, url }` 반환. 썸네일은 만들지 않는다(FR-013).
  5. 미리보기에 표시 → [저장] 때 `PATCH /api/me/profile { "profileImageId": id }`로 연결.
  - 완료 여부는 `width IS NOT NULL`로 판단한다(스키마 변경 없이).
  - 외부 호출(저장소 inspect·delete)은 DB 트랜잭션 밖에서 한다(헌법 V).
  - `purpose: POST`는 008 범위라 지금은 400 `IMAGE_PURPOSE_NOT_SUPPORTED`로 거부한다(008이 받아들이도록 바꾼다).
- **Rationale**: 11 §4-1, 04 §4-1. 1분 20장은 04 §4-2·42 §10. 인증 전 차단은 001 `AccountGuard.requireWritable`이 이미 "사진" 쓰기 가드로 정해 둔 것을 그대로 쓴다.
- **Alternatives considered**: 서버가 파일을 받아 다시 인코딩 — 04 결정(사진은 서버를 거치지 않음)과 다르다.

## R-8. 서버 이미지 검사 (FR-013, SC-003)

- **Decision**: `media.domain.ImageHeaderInspector`(순수 함수): 앞부분 바이트로 형식 판별(JPEG `FF D8 FF`, PNG 8바이트 서명, GIF `GIF87a/GIF89a`, WebP `RIFF....WEBP`)과 가로·세로(PNG IHDR, GIF 논리 화면, JPEG SOFn 표식, WebP `VP8 `/`VP8L`/`VP8X`)를 읽고, 메타데이터 조각(JPEG APP1 `Exif`, PNG `eXIf`, WebP `EXIF`·`XMP ` 조각 또는 VP8X 플래그)이 있으면 표시한다. 파일 전체를 디코딩하지 않는다. 프로필 규격은 설정 `blog.image.profile.size=256`, `max-bytes=1MiB`.
- **Rationale**: 브라우저 검사는 우회할 수 있으므로 서버가 다시 거부한다(04 §4-1 ⑤). 메타데이터 검사로 "촬영 위치가 남지 않는다"(US2-1)를 서버에서도 보장한다. JPEG의 SOF가 앞부분 64KiB 밖에 있으면(큰 EXIF) 판별 불가로 거부한다 — 프로필 이미지는 256×256이라 정상 파일에서 생기지 않는다.
- **Alternatives considered**: `ImageIO` 전체 디코딩 — WebP 리더가 JDK에 없고, 큰 파일 디코딩은 비싸다.

## R-9. 연결·해제와 정리 작업 (FR-014~FR-016, SC-003, SC-004)

- **Decision**:
  - `media.application.ProfileImageService`(account가 부름):
    - `checkCandidate(memberId, imageId)`: 행이 있고, `uploader_id = memberId`, `purpose = PROFILE`, 업로드 완료(`width IS NOT NULL`), `detached_at IS NULL`(연결이 끊겨 삭제를 기다리는 이미지는 "삭제된 이미지"로 본다)이면 통과. 아니면 `INVALID_PROFILE_IMAGE` — 없음·남의 것·글용·미완료·삭제 대기를 구분하지 않는다(헌법 III 정신, SC-003).
    - `attach(memberId, imageId)`: 같은 트랜잭션에서 이미지 행을 `FOR UPDATE`로 잠그고 `checkCandidate`를 다시 판정 → `status = ATTACHED`, `detached_at = NULL` → 공개 주소 반환(호출자가 `member.profile_image_url`에 복사).
    - `detach(imageId, now)`: 이전 이미지 `detached_at = now`(상태는 `ATTACHED` 유지, 04 §4-4).
    - 지금 연결된 것과 같은 ID를 다시 보내면 아무것도 하지 않는다.
  - account `ProfileService`는 위 셋과 `member.profile_image_id`·`profile_image_url` 변경을 **한 트랜잭션**에서 한다(FR-016 원자성).
  - 정리: `media.application.ImageCleanupService.runOnce()` + `ImageCleanupJob`(`@Scheduled`, 기본 매일 04:30 Asia/Seoul, `blog.image.cleanup.cron`; 테스트는 `blog.image.cleanup.enabled=false`로 끄고 서비스를 직접 호출).
    - 대상: `status = 'TEMP' AND created_at < now − 24h` 또는 `detached_at < now − 7d`(`ix_image_cleanup_temp`, `ix_image_cleanup_detached` 사용, 한 번에 최대 500건).
    - 방어: SPI `ImageReferenceLookup.referencedIds(ids)`(account 구현: `member.profile_image_id IN (...)`)가 참조 중이라고 하면 건너뛴다. 008은 `post_image` 참조를 같은 SPI의 구현으로 더한다.
    - **순서: DB 행 삭제(조건 재확인 `DELETE … WHERE id = ? AND (조건)`, 한 건씩 짧은 트랜잭션) → 커밋 후 저장소 파일 삭제.** 저장소 삭제가 실패하면 키를 Redis 집합 `img:orphan-keys`에 넣고 다음 실행 첫머리에서 다시 지운다.
  - 시각은 주입한 `Clock`. `image.created_at`·`detached_at`은 앱이 `Clock`으로 넣는다.
- **Rationale**: 11 §4-4, 04 §4-4. 04 §4-4는 "저장소 삭제 후 DB 행 삭제"라고 적었지만, 그 순서는 정리 대상 선택과 사용자의 [저장] 사이 경합에서 **연결된 이미지의 파일을 지울 수 있다**(행 삭제가 조건 재확인으로 0건이 되어도 파일은 이미 없음). 행을 먼저 조건부로 지우면(이미지 행은 `attach`가 잠금) 연결된 이미지는 절대 지워지지 않고, 저장소 실패는 재시도 집합으로 04의 "다음 배치에서 재시도"를 그대로 지킨다. **04 문서와의 차이**로 기록한다(U-4).
- **Alternatives considered**: 저장소 삭제를 DB 트랜잭션 안에서 — 헌법 V 위반. 정리 대상에 "처리 중" 표시 — 공통 ERD에 컬럼이 필요하다(헌법 II).

## R-10. 기본 이미지 (FR-018)

- **Decision**: `account.application.ProfileAvatar(handle, nickname, imageUrl)` 값 + Thymeleaf 조각 `fragments/avatar :: avatar(avatar, size)`. 이미지가 있으면 `<img alt="">`(23 §4: 옆에 닉네임이 있으므로 빈 대체글), 없으면 `<span class="avatar avatar-c{n}" aria-hidden="true">` 안에 닉네임 첫 글자(코드 포인트 1개, 영문이면 대문자). 색 번호 `n = floorMod(handle.hashCode(), 8)`(Java 명세로 고정된 해시라 항상 같다). 8색은 흰 글자와 대비 4.5:1 이상인 진한 색으로, 라이트·다크 모두 같은 배경·같은 흰 글자를 쓰므로 두 모드에서 대비가 같다. 단위 테스트가 WCAG 대비를 계산해 확인한다. 파일을 저장하지 않는다.
- **Rationale**: 11 §4-3. 글자와 배경의 대비는 페이지 모드와 무관하게 원 안에서 정해진다.
- **Alternatives considered**: SVG 데이터 URI — CSS로 충분하고 CSP `img-src data:` 의존이 늘지 않는다.

## R-11. 소셜 프로필 사진 (FR-019~FR-023, SC-005)

- **Decision**:
  - `account.domain.SocialPictureUrlPolicy.sanitize(provider, rawUrl)`: `https`, userinfo 없음, 포트 없음(또는 443), 호스트가 **정확히** `lh3.googleusercontent.com`(GOOGLE) / `avatars.githubusercontent.com`(GITHUB)일 때만 통과(허용 호스트는 설정 `blog.profile.social-picture.allowed-hosts`). Google은 경로 끝의 크기 매개변수(`=s96-c` 등 `=s숫자…`)를 지우고 `=s256-c`, GitHub은 `s` 쿼리를 지우고 `s=256`을 붙인다. 통과하지 못하면 화면에 넘기지 않는다(미리보기·체크 상자도 없음).
  - 001 `SocialSignupController`가 마무리 화면에 넘기는 `pictureUrl`과 완료 후 세션에 남기는 값을 이 정책을 거친 값으로 바꾼다. 완료 후, "프로필 사진 사용"이 켜져 있고 사진 주소가 있고 **공급자가 인증한 이메일로 가입한 경우**(인증 전 회원은 사진 업로드가 막혀 있으므로)에만 `/settings/social-picture`로 보낸다. 그 밖은 지금처럼 `/`.
  - `/settings/social-picture`(로그인 필요): 세션 값을 **한 번 꺼내 지우고** 화면에 `data-picture-url`로 넘긴다. `social-picture.js`가 `fetch(url, {mode:'cors', credentials:'omit'})`를 **5초** `AbortController`로 받아 가운데 정사각형 → 256×256 WebP 0.85 → R-7 흐름(presign → PUT → complete) → `PATCH /api/me/profile {profileImageId}` → 성공하면 `/`. 실패(시간 초과·CORS·업로드 거부)면 이미지 없이 "소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요"와 [계속하기]·[설정으로] 링크를 보여준다. JS가 꺼져 있으면 같은 안내와 링크만 보인다.
  - 서버는 소셜 사진 주소로 요청하지 않고 DB에 저장하지도 않는다. 프로필 주소는 complete를 통과한 우리 저장소 주소뿐이다.
- **Rationale**: 11 §4-2, 결정 기록 "소셜 프로필 사진". 계정이 생긴 뒤에만 업로드할 수 있다(07: 마무리 전 계정 미생성). 별도 화면으로 두면 사진 처리 실패가 가입·홈 화면 동작과 섞이지 않는다.
- **Alternatives considered**: 홈 화면 스크립트에서 처리 — 모든 홈 요청에 세션 확인이 붙는다. 서버가 내려받기 — SSRF(11 R-6 기각).

## R-12. CSP 확장 (헌법 IV)

- **Decision**: `SecurityConfig`의 고정 CSP 문자열을 `ContentSecurityPolicy` Bean으로 바꿔 설정에서 조립한다: `img-src 'self' data: blob: {저장소 공개 출처} https://lh3.googleusercontent.com https://avatars.githubusercontent.com`, `connect-src 'self' {저장소 출처} {공개 출처} https://lh3.googleusercontent.com https://avatars.githubusercontent.com`. 나머지 지시어는 그대로. `blob:`은 자르기 미리보기(`URL.createObjectURL`)에 필요하다.
- **Rationale**: `default-src 'self'`만으로는 브라우저 직접 업로드(PUT)와 소셜 사진 받기가 막힌다. 04 §6-1 "CSP `connect-src`·`img-src`에 저장소 주소".
- **Alternatives considered**: 화면별 CSP — 같은 주소들이 여러 화면(설정, 소셜 가입, 블로그 상단 사진)에 필요하다.

## R-13. 비밀번호 변경 (FR-024~FR-027, SC-006)

- **Decision**: `account.application.PasswordChangeService.change(CurrentUser, PasswordChangeCommand, String currentSessionId)`.
  - 판정 순서: 로그인 필요 → 로그인 수단이 `LOCAL`이 아니면 400 `PASSWORD_NOT_SUPPORTED` → 잠금 중이면 429 `PASSWORD_CHANGE_LOCKED`(+`Retry-After`) → 현재 비밀번호 BCrypt 비교 실패 → 400 `CURRENT_PASSWORD_MISMATCH`(연속 실패 +1, 5회째 15분 잠금) → 새 비밀번호 = 현재 → 400 `PASSWORD_SAME_AS_CURRENT` → 001 `PasswordPolicy` 위반·확인 불일치 → 400 `VALIDATION_FAILED`(`newPassword` 칸에 위반 코드 목록의 첫 번째, `newPasswordConfirm`에 `PASSWORD_MISMATCH`).
  - 잠금 키: `auth:pw-change-fail:{memberId}`, `auth:pw-change-lock:{memberId}`(001 `LoginAttemptStore`의 Lua 스크립트를 키를 받는 메서드로 넓혀 재사용). 5회·15분은 001 `blog.auth.login.*`와 같은 값을 따로 둔 설정 `blog.auth.password-change.*`. 성공하면 실패 수를 지운다.
  - 성공: 해시 교체(`AuthIdentity.changePasswordHash`) → 이벤트 `PasswordChanged(memberId, email, keepSessionId)` → **커밋 후** ① `SessionRevoker.revokeAllExcept(memberId, keepSessionId)`(다른 기기 세션 삭제) ② 알림 메일 `mail/password-changed`("비밀번호가 변경됐어요. 본인이 아니라면 [비밀번호 재설정]" — 링크는 `/password/forgot`). 둘은 서로의 실패와 무관하다.
  - 지금 기기: 컨트롤러가 서비스 성공 뒤 `request.changeSessionId()`로 세션 ID를 새로 발급한다. 다른 세션을 지울 때는 바꾸기 **전**의 ID를 남긴다(Spring Session은 응답을 마칠 때 이전 ID의 저장소 키를 새 ID로 옮기므로, 이전 ID를 지우면 지금 기기 세션까지 사라진다).
  - 정지 회원은 로그인할 수 없으므로 별도 검사가 없다(42 §9). 인증 전 회원도 바꿀 수 있다(42 §9).
- **Rationale**: 11 §6-2, 결정 기록 "비밀번호 변경". 001 `SessionRevoker`(principal 인덱스)와 메일 포트를 그대로 쓴다. 판정 순서를 "현재 비밀번호 → 새 비밀번호 규칙"으로 둬서 세션 탈취자가 정책 메시지로 정보를 얻기 전에 현재 비밀번호를 요구한다.
- **잠금 응답 코드**: 원문은 정하지 않았다. 002 R-14와 같이 요청 제한 성격이라 429를 쓴다(U-2).
- **Alternatives considered**: 모든 세션을 지우고 지금 기기는 다시 로그인 — 11 §6-2 "지금 기기는 유지"와 다르다.

## R-14. 새 글 기본 공개 범위 (FR-028)

- **Decision**: `PATCH /api/me/settings { "defaultVisibility": "PUBLIC" | "PRIVATE" }` → `AccountSettingsService.changeDefaultVisibility`. 그 밖의 값(`FRIENDS` 포함) → 400 `VALIDATION_FAILED`(`defaultVisibility`: `INVALID_VISIBILITY`). 공통 DB CHECK(`ck_member_default_visibility`)가 두 값만 허용하므로 친구 공개 구현자는 마이그레이션·검사 값을 **추가**한다. 글 기능(004·005)은 `AccountSettingsService.defaultVisibility(memberId)`로 읽는다(새 글이 이 값으로 시작 — 글 기능이 아직 없어 이 기능은 값 저장·조회까지 테스트).
- **Rationale**: 11 §6-3, 06 §5, 51.
- **Alternatives considered**: 글 기능이 `member`를 직접 조회 — 헌법 I 위반.

## R-15. 설정 화면 (FR-001~FR-003, FR-029)

- **Decision**: `GET /settings`(SSR, `AccountGuard.requireLoggedIn` → 비회원은 `/login?redirect=/settings`로 303). 프로필 칸(이미지·[이미지 변경]·[기본 이미지로], 닉네임 + 다음 변경 가능일·제한 중이면 비활성, 소개 + 글자 수, 블로그 주소 "변경할 수 없어요"), 계정 칸(이메일·로그인 수단 읽기 전용, `LOCAL`만 [비밀번호 변경] 폼, 기본 공개 범위 라디오), 회원 탈퇴 진입점(`/settings/withdraw` 링크 — 처리는 023 범위, 지금은 안내만). 저장은 JS(`settings.js`)가 JSON API를 부르고 칸별 오류를 그린다. 이메일 인증 전이면 [이미지 변경]을 숨기고 "이메일 인증 후 사진을 올릴 수 있어요"를 보인다(서버도 403).
- **Rationale**: 11 §2 화면 그대로. 업무 규칙은 Service에만 있다.
- **JS 없는 브라우저**: 프로필 이미지 자르기가 JS를 전제로 하므로 화면은 JS를 쓴다. JS가 없으면 "이 화면은 JavaScript가 필요해요"를 보인다(U-5).
- **Alternatives considered**: 폼 POST 병행 — 같은 규칙을 두 표현으로 묶는 코드가 늘어난다. REST 계약만으로 다른 팀원도 같은 Service를 쓴다.

## R-16. 테스트 전략 (헌법 VI)

- **Decision**: 001·002 테스트 기반(`IntegrationTestBase`: PostgreSQL 18·Redis·Mailpit 정적 컨테이너)에 **저장소 컨테이너**(`pgsty/silo` 고정 태그, `GenericContainer`, `server /data`, `/minio/health/live` 대기)를 더하고 `blog.storage.*`를 동적 속성으로 넣는다. 업로드는 실제 사전 서명 주소에 Java `HttpClient`로 PUT한다. 테스트 이미지는 `ImageIO`로 PNG·JPEG를 만들고, WebP는 VP8L 머리말을 가진 바이트를 직접 만든다(서버는 머리말만 읽음). EXIF가 든 JPEG는 APP1 조각을 끼워 만든다. 시간은 `MutableClock`.
  - 통합: 프로필 저장(부분 저장·전체 실패·모든 오류 한 번에·XSS 문자열이 블로그 상단에 글자로·30일 제한·동시 저장), 이미지(presign 권한 401/403/한도 429·complete 거부 5종·연결 거부 4종·교체·기본으로·정리 24h/7d/연결 보호·고아 키 재시도), 소셜 사진(주소 거르기·세션 1회 전달·복사 후 주소가 저장소 주소), 비밀번호(두 기기·메일·잠금·소셜 거부), 설정 화면(비회원 리다이렉트·읽기 전용 표시·LOCAL만 버튼).
  - 단위: `BioRules`, `ImageHeaderInspector`, `SocialPictureUrlPolicy`, `ProfileAvatar`(색 고정·대비 4.5:1).
- **Rationale**: 소유·권한 검사는 통합 테스트 필수(헌법 VI). 저장소도 실제 S3 API로 검증한다.
- **Alternatives considered**: 저장소 대역(가짜 `ImageStorage`) — 사전 서명·Content-Type 일치 같은 실제 동작을 놓친다.

---

## 남은 확인 사항 (계획 진행을 막지 않음)

| # | 항목 | 현재 선택 | 확인 주체 |
|---|---|---|---|
| U-1 | 프로필 오류의 HTTP 코드 섞임 | 400, 충돌 종류만이면 409 (R-4) | 팀 확인 권장 |
| U-2 | 비밀번호 변경 잠금 응답 | 429 `PASSWORD_CHANGE_LOCKED` + `Retry-After` (R-13) | 원문 미정, 002 R-14와 같은 선택 |
| U-3 | 소개 금칙어의 띄어쓰기 우회 | 닉네임과 같은 필터만(공백 제거 변형 없음) (R-3) | 팀 (09 §4) |
| U-4 | 정리 작업 순서(04 §4-4와 다름) | 행 조건부 삭제 → 커밋 후 파일 삭제, 실패 키는 재시도 집합 (R-9) | 008 담당과 맞춤 |
| U-5 | JS 없는 설정 화면 | 안내만(저장은 JS) (R-15) | 원문 미정 |
| U-6 | 인증 전 소셜 가입(이메일 직접 입력)의 소셜 사진 | 복사하지 않음(업로드 권한 없음) (R-11) | 원문 미정, 42 §10을 따름 |
| U-7 | 연결이 끊긴(삭제 대기) 내 이미지를 다시 연결 | 거부(`INVALID_PROFILE_IMAGE`, "삭제된 이미지"로 봄) (R-9) | 원문 미정, 보수적 선택 |
| U-8 | 008 범위로 미룬 저장소 항목 | 앱 전용 키, 23 §2-3 검증 자동화, `LocalImageStorage`, 용량·하루 장수 (R-5) | 008 |

---

## 구현 메모 (/speckit-implement, 2026-10-07)

tasks.md와 다르게 하거나 tasks.md에 없는 세부를 정한 곳. 계약(contracts/)의 동작은 그대로다.

| # | 내용 | 이유 |
|---|---|---|
| I-1 | 사진 업로드 기반(008)이 없어 `media` 모듈에 프로필에 필요한 최소만 만들었다: `ImageStorage`(04 §4-1 시그니처) + `S3ImageStorage`(AWS SDK v2 2.55.12, path-style, SigV4, 체크섬 `WHEN_REQUIRED`), `ImageUploadService`(presign·complete, `purpose=PROFILE`만), `ProfileImageService`, `ImageCleanupService`/`ImageCleanupJob`, SPI `ImageReferenceLookup`. 008은 `purpose=POST`·썸네일·용량·`LocalImageStorage`·앱 전용 키를 같은 클래스·테이블에 **추가**한다(`IMAGE_PURPOSE_NOT_SUPPORTED`를 풀면 됨). | 사용자 지시, research R-5·R-6 |
| I-2 | 저장소 로컬·테스트 이미지는 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`(04 §6-1 고정 태그). compose 서비스 `storage`, 호스트 포트 `${STORAGE_PORT:-9000}`, 루트 계정 기본값 `blogminio`/`blogminio-dev-secret`은 로컬 전용(운영 비밀 아님). 테스트는 Testcontainers `GenericContainer`(`server /data`, `/minio/health/live` 대기)를 `IntegrationTestBase`의 정적 컨테이너로 더했다. 개발·테스트는 시작할 때 버킷과 "익명은 `images/*` `GetObject`만" 정책을 만든다(`blog.storage.create-bucket`). | R-5 |
| I-3 | `S3ImageStorageIT`에서 23 §2-3 검증 중 일부(정상 PUT 200, 서명과 다른 `Content-Type` 403, 익명 목록 403, `images/*` 밖 익명 읽기 403, 공개 주소 익명 읽기 200)를 자동화했다. 서명 위조·경로 변경·만료 검증과 CORS 거부 검증은 008 범위로 남겼다. | U-8 |
| I-4 | `media`가 1분 20장 제한에 001·002의 `account.infra.RedisRateLimiter`를 그대로 쓴다(Redis 키 `img:upload:{memberId}`). 이 클래스는 account 데이터가 아닌 범용 카운터라 헌법 I의 "다른 모듈 저장소·테이블 직접 사용"에 해당하지 않는다고 보고 옮기지 않았다. `shared`로 옮기는 것은 후속 정리 후보. | 기존 클래스 재사용 지시 |
| I-5 | 업로드 완료 여부는 스키마 변경 없이 `image.width IS NOT NULL`로 판단한다. `image.created_at`·`detached_at`은 앱 `Clock`으로 넣어 테스트에서 24시간·7일을 재현한다. 저장 키의 연·월은 UTC 기준. | 헌법 II |
| I-6 | complete 거부 이유 `detail`은 `MISSING`·`SIZE`·`CONTENT_MISMATCH`·`DIMENSION`·`METADATA`(presign은 `TYPE`·`SIZE`). 거부하면 저장소 파일을 지우고(실패하면 `img:orphan-keys`) 행도 지운다. 이미 완료된 사진의 complete는 같은 응답(멱등). 남의 사진·없는 사진 complete는 404. | R-7 |
| I-7 | 프로필 저장 요청 본문은 Jackson 3(`tools.jackson.databind.JsonNode`) 트리로 읽어 "보내지 않음"과 `null`을 구분하고, 타입이 틀린 칸은 다른 칸 검사와 함께 `INVALID_VALUE` 항목으로 돌려준다. JSON이 아니면 400(프레임워크 기본). | R-2 |
| I-8 | 칸별 오류는 `shared.error.FieldError` + `ProfileValidationException`(→ `VALIDATION_FAILED`, `errors[]`)으로 모았다. `ErrorResponse`에 `errors`·`detail`을 null이면 생략되는 필드로 더해 001·002 응답 모양은 그대로다. 비밀번호 정책 위반·확인 불일치도 같은 모양(`newPassword`: 001 `password.*` 코드 중 첫 번째, `newPasswordConfirm`: `PASSWORD_MISMATCH`). | R-4 |
| I-9 | CSP는 `SecurityConfig` 상수 대신 `ContentSecurityPolicy` Bean이 조립한다(저장소 공개·API 출처, 소셜 사진 호스트 2개, `blob:`). `SecurityHeadersIT`는 Bean 값과 비교하고 조립 결과를 따로 검사한다. | R-12 |
| I-10 | `AuthorDisplay`에 `profileImageUrl`을 더하면서 002의 3인자 생성자·`of(handle, nickname, withdrawnAt)`를 유지했다. 탈퇴 회원 아이콘은 사진·첫 글자 없이 회색(`?`). `MemberSummaryQuery`·`BlogOwner`가 사진 주소를 채운다. 목록·글 상세·댓글 화면(009·010·014)은 아직 없어 블로그 상단과 표시 값으로 즉시 반영을 확인했다(SC-007). | R-10 |
| I-11 | 001 `SocialSignupIT.completionPagePrefillsNicknameAndFixedPrefixHandle`의 "프로필 사진 사용" 기대를 바꿨다. 001 테스트 도우미의 사진 주소(`https://example.com/p.png`)는 003 FR-020에서 허용 호스트가 아니라 화면에 넘기지 않는다. 허용 호스트 사진 칸은 `SocialPictureIT`가 확인한다. | FR-020 |
| I-12 | `SocialSignupController`(001)는 완료 후 "사진 사용 + 거른 주소 + 공급자 인증 이메일"일 때만 `/settings/social-picture`로 보낸다(U-6). 그 화면이 세션 값을 한 번 꺼내 지운다. 사진 미리보기 `<img>`는 `referrerpolicy="no-referrer"`. | R-11 |
| I-13 | `LoginAttemptStore`(001)에 키를 직접 받는 메서드(`isLockedKey`, `recordFailureKeys`, `clearKey`, `remainingSeconds`)를 더하고 기존 이메일 메서드가 위임하게 했다. 비밀번호 변경 잠금 키 `auth:pw-change-fail:{memberId}`·`auth:pw-change-lock:{memberId}`, 값은 `blog.auth.password-change.*`(`AuthProperties.passwordChange`). 정책 위반·같은 비밀번호는 잠금 횟수에 넣지 않는다(현재 비밀번호 불일치만). | R-13 |
| I-14 | 비밀번호 변경 후 다른 세션 삭제는 `SessionRevoker.revokeAllExcept(memberId, 바꾸기 전 세션 ID)`를 커밋 후 리스너에서 부르고, 컨트롤러가 그 뒤 `request.changeSessionId()`를 한다. `PasswordChangeIT`가 실제 로그인한 두 브라우저로 "A 쿠키 값 바뀜·로그인 유지, B 로그인 필요"를 확인한다. | R-13 |
| I-15 | 정리 작업은 `@EnableScheduling`(`shared.config.SchedulingConfig`) + `blog.image.cleanup.enabled`(테스트 false). 저장소 삭제 실패 재시도는 `@MockitoSpyBean ImageStorage`로 흉내 냈다. | R-9 |
| I-16 | T274 확인(2026-10-07): compose(포트 55432/56379/51025/58025/59000, 프로젝트 이름 `blog003`) + jar(dev)로 띄워 시작 시 버킷 생성 확인, 가입·인증은 curl, 나머지는 내장 브라우저에서 화면 JS로 확인했다 — 소개 저장("저장했어요", 글자 수 23), 사진 선택(800×500 JPEG) → 자르기(확대 1.5) → **브라우저에서 MinIO로 사전 서명 PUT 성공(CORS 통과)** → 256×256 WebP 미리보기 → [저장] 후 `profileImageUrl` 반영, [기본 이미지로] → 기본 아이콘 `avatar-c3`(서버 해시와 같음)·첫 글자 표시 → 저장 후 NULL, 공개 범위 라디오 → `PRIVATE`, 비밀번호 폼(틀린 현재 비밀번호 문구 → 성공 후 로그인 유지), 소셜 사진 처리 함수(가운데 정사각형 → WebP → 업로드), 375px에서 가로 스크롤 없음(자르기 영역 포함), 블로그 상단 소개 이스케이프. **확인하지 못한 것**: 실제 Google·GitHub 사진 서버에서 브라우저로 받기(CORS·5초 제한, OAuth 앱 필요), 운영 NHN MinIO. | |
| I-17 | 테스트 수: 시작 241개 → 완료 313개(+72, `./gradlew clean build` 통과). | T275 |
