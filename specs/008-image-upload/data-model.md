# Data Model: 이미지 업로드 (008)
새 마이그레이션 없음. V1 `image`(`thumb_storage_key`, `thumb_size_bytes`, `width`, `height`, `status`, `purpose`, `detached_at`, `ix_image_uploader`), `post_image`.

| 상태 | 뜻 |
|---|---|
| TEMP | 승인됨·완료됨·아직 어느 글에도 연결 안 됨(24h 뒤 정리) |
| ATTACHED, detached_at NULL | 글(또는 프로필)에 연결됨 |
| ATTACHED, detached_at 있음 | 연결이 끊김(7일 뒤 정리) |

Redis: `img:upload:{memberId}`(1분 20), `img:daily:{memberId}:{yyyyMMdd}`(KST, TTL 2일).
오류: 400 `IMAGE_INVALID`(+`detail`: TYPE·SIZE·MISSING·CONTENT_MISMATCH·DIMENSION·METADATA·THUMBNAIL·FRAMES), 409 `STORAGE_QUOTA_EXCEEDED`, 429 `DAILY_UPLOAD_LIMIT`·`RATE_LIMITED`, 발행 400 `INVALID_IMAGE`.
설정 `blog.image.post.{max-bytes:10485760, thumb-max-bytes:1048576, thumb-max-width:640, max-dimension:10000, gif-max-dimension:1920, gif-max-frames:300}`, `blog.image.{quota-bytes:1073741824, daily-limit:200}`, `blog.storage.{type:s3, local-dir, local-secret}`.
