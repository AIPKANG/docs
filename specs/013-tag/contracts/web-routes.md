# Contract: 태그 (013)
| 경로 | 결과 |
|---|---|
| `GET /tags/{이름}` | 태그별 공개 글 카드 9개, 공개 글 수 · 정리 안 된 이름 301 · 형식 밖 404 |
| `GET /tags` | 상위 100개 |
| `GET /api/tags/{이름}/posts?cursor=` | `{items, nextCursor}` |
| `GET /api/tags?limit=100` | `[{name, postCount}]` |
| `GET /api/tags/suggest?q=` | `[{name, postCount, mine}]` · 401 · 429 |
| `GET /@{handle}?tag=` · `/api/members/{handle}/tags` · `/api/members/{handle}/posts?tag=` | 블로그 태그 필터 |
