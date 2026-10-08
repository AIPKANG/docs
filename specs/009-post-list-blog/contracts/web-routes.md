# Contract: 목록 (009)
| 경로 | 결과 |
|---|---|
| `GET /` (`?cursor=`) | 첫 9개 카드(SSR), [더 보기] 링크 또는 "모든 글을 다 봤어요", 빈 상태 |
| `GET /@{handle}` (`?cursor=`) | 프로필 머리말(+공개 글 수) + 그 회원 공개 글 카드, 빈 상태(본인/남) |
| `GET /api/posts?cursor=` | `{items, nextCursor}` (9개) · 400 `INVALID_CURSOR` |
| `GET /api/members/{handle}/posts?cursor=` | 같음 · 404 없는 블로그 |
Service: `PostListQuery.feed(cursor)`, `blog(authorId, cursor)`, `publicCount(authorId)`.
