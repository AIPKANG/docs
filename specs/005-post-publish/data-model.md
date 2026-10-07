# Data Model: 글 발행·수정 (005)

**새 마이그레이션 없음.** V1 `post`(`published_at`, `first_public_at`, `edited_at`, `excerpt`, `thumbnail_url`, `render_version`, CHECK `ck_post_published`·`ck_post_public_at`·`ck_post_edited_at`), `post_draft`, `tag`(`uq_tag_name`, `ck_tag_name`), `post_tag`(`position` 0..99)을 쓴다.

| 시각 | 발행(처음) | 다시 발행 | 자동 저장 반영(004) |
|---|---|---|---|
| `published_at` | now | 그대로 | 그대로 |
| `first_public_at` | 공개면 now | 비어 있고 공개면 now, 아니면 그대로 | 그대로 |
| `edited_at` | 그대로(NULL) | now | 그대로 |
| `updated_at` | now | now | now(임시글만) |
| `edit_version` | 현재 + 1 | 현재 + 1 | 버퍼 버전 |

Redis: `idem:publish:{memberId}:{key}` String `IN_PROGRESS|hash` 또는 `DONE|hash|json`, 10분.

사건: `PostPublished(postId, authorId, visibility, firstPublicAt)`, `PostEdited(postId, authorId, visibility)`.

오류 코드: `VALIDATION_FAILED`(+`errors[]`: `TITLE_REQUIRED`, `TITLE_TOO_LONG`, `CONTENT_REQUIRED`, `CONTENT_TOO_LONG`, `TOO_MANY_TAGS`, `INVALID_TAG`, `TAG_TOO_LONG`, `TAG_BANNED_WORD`, `INVALID_VISIBILITY`, `PENDING_IMAGES`), `CONTENT_TOO_COMPLEX`, `EDIT_CONFLICT`(+`server`), `IN_PROGRESS`(409), `IDEMPOTENCY_KEY_REUSED`(422), `INVALID_REQUEST`, 401/403/404.

설정: `blog.post.max-tags: 10`, `blog.post.publish.idempotency-ttl: 10m`.
