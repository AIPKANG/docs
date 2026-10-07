# Contract: 이미지 업로드 (008)

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/images/presign` | `{purpose:"POST", contentType, size, thumbSize?, originalName?}` | 200 `{imageId, uploadUrl, method, headers, expiresAt, thumbUploadUrl?, thumbHeaders?}` | 400 `IMAGE_INVALID`, 401, 403, 409 `STORAGE_QUOTA_EXCEEDED`, 429 `RATE_LIMITED`/`DAILY_UPLOAD_LIMIT` |
| `POST /api/images/{id}/complete` | — | 200 `{imageId, url, thumbUrl?, width, height}` | 400 `IMAGE_INVALID`(파일 삭제), 404 |
| `GET /api/me/storage` | — | 200 `{usedBytes, quotaBytes, todayCount, dailyLimit}` | 401 |
| `PUT /api/images/local-upload?key&exp&sig` | 파일 바이트(대체 저장소만) | 200 | 403 |
| `GET /media/{key}` | (대체 저장소만) | 파일, `Cache-Control: public, max-age=31536000, immutable` | 404 |

공개 Service(media): `PostImageService.sync(postId, authorId, urls, now)`, `ownedPostImageUrls(authorId, urls)`, `thumbnailUrlOf(url)`, `StorageUsageQuery.usage(memberId)`.
