# Source Notes: 008-image-upload

## plan 단계에서 참고할 기술 결정

- 업로드 흐름 ①~⑥: `POST /api/images/presign {contentType, size, thumbSize}` → 업로드 주소 2개(5분) → 브라우저 직접 PUT → `POST /api/images/{id}/complete` (docs/04-draft-and-image.md §4-1, docs/10-post-list.md §6)
- 저장소: MinIO, AWS SDK for Java v2 `S3Client`/`S3Presigner` + `endpointOverride` + `forcePathStyle(true)`, SigV4 필수 (04 §4-1, §6-1; 01 결정 기록 2026-10-06)
- 로컬 이미지 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`, compose 예시, `MINIO_API_CORS_ALLOW_ORIGIN`, 클라이언트 `pgsty/mc`, AGPLv3 (04 §6-1)
- 검증 스크립트 `scripts/check-storage.sh`, `scripts/lib/presign_check.py` (11가지 PASS), Testcontainers로 자동화 (04 §6-1, 23 §2-3 — RustFS 대신 고정 MinIO 포크 이미지)
- `ImageStorage` 인터페이스(prepareUpload / inspect / publicUrl / delete), 대체 `LocalImageStorage`(`PUT /api/images/{id}/content`) (04 §4-1)
- 저장 키 `images/{yyyy}/{MM}/{uuid}.{ext}`, 썸네일 `{uuid}_thumb.webp`(`image.thumb_storage_key`), `original_name` 컬럼 (04 §4-2, 10 §6)
- `Cache-Control: public, max-age=31536000, immutable` + CDN, Redis 카운터 1분 20장 (04 §4-2)
- 오프라인: IndexedDB `pendingImages`, 본문 `![업로드 대기](local:…)`, `URL.createObjectURL`, 발행 시 `local:` 있으면 400 (04 §4-3, 05-publish.md §7)
- 정리 배치: `image.status TEMP/ATTACHED`, `detached_at`, `post_image`, `purpose = PROFILE` + `member.profile_image_id`, 트랜잭션 밖 저장소 삭제 후 DB 행 삭제 (04 §4-4, §5; 11-profile.md §4-4)
- 스키마: `image.status`, `width`, `height`, `detached_at`, `post_image` N:M (04 §5), `image.thumb_size_bytes integer` nullable + CHECK ≤ 1048576 (23 §7)
- 용량: `blog.image.quota-bytes`(1GB), `blog.image.daily-limit`(200), presign 트랜잭션에서 `SELECT … FROM member WHERE id = :me FOR UPDATE` 후 합계 SQL(`ix_image_uploader`), Redis `img:daily:{memberId}:{yyyyMMdd}` INCR TTL 2일, 오류 `409 STORAGE_QUOTA_EXCEEDED` / `429 DAILY_UPLOAD_LIMIT` (23 §3)
- `GET /api/me/storage` → `{usedBytes, quotaBytes, todayCount, dailyLimit}` (23 §3-1)
- GIF: 서버 프레임 수 `ImageReader.getNumImages`, AST 변환 규칙(링크로 감싼 썸네일 img), `/js/gif-play.js`, CSS `a[href$=".gif"]::after` (23 §5; 12 §2 ②)
- 버킷 정책: 익명 `GetObject`는 `images/*`만, `ListBucket` 금지, CORS는 우리 출처 PUT·`Content-Type`만 (23 §2-2, 04 §6-1)
- CSP `connect-src`·`img-src`에 저장소 주소 (04 §6-1, 검증 H3 `verification/reports/2026-10-06/summary.md`)
- 권한: 인증 전 403 (07-auth.md), 남의 사진 연결 400 `INVALID_PROFILE_IMAGE` 등 (42 §10, 11 §5)
