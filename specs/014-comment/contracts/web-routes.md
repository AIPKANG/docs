# Contract: 댓글 (014)
| 경로 | 결과 |
|---|---|
| `POST /api/posts/{postId}/comments` `{content, replyToCommentId?}` | 201 댓글 · 400 · 401 · 403 · 404 · 429 |
| `GET /api/posts/{postId}/comments?cursor=&around=` | `{items, nextCursor, prevCursor?}` · 404 |
| `GET /api/comments/{rootId}/replies?cursor=` | `{items, nextCursor}` · 404 |
| `PATCH /api/comments/{id}` `{content}` | 200 · 404 · 409 `COMMENT_HIDDEN` · 429 |
| `DELETE /api/comments/{id}` | 204 · 404 |
| `POST /@{handle}/posts/{id}/comments` (폼, 스크립트 없음) | 303 상세 `#comment-{id}` |
| `GET /@{handle}/posts/{id}?comment={id}` · `?commentCursor=` | 그 댓글부터 / 다음 20개 |
