# Data Model: 공개 범위 (006)

새 마이그레이션 없음. `post.visibility`(`ck_post_visibility`: PUBLIC/PRIVATE), `post.first_public_at`, `member.default_visibility`, `member.withdrawn_at`.

사건 `PostVisibilityChanged(postId, authorId, from, to, firstPublicAt)`.
`PostFacts(id, authorId, status, visibility, authorWithdrawn)` — 판정 입력.
오류: 400 `INVALID_VISIBILITY`, 401, 403, 404.
