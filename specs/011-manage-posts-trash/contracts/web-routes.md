# Contract: 내 글 관리·휴지통 (011)
| 요청 | 결과 |
|---|---|
| `GET /manage/posts?tab&visibility&cursor` | 세 탭 화면(20개, 개수, [더 보기] 링크). 비회원 → 로그인 |
| `GET /api/me/posts?tab&visibility&cursor` | `{items, nextCursor, counts}`(counts는 커서 없을 때만) · 400 `INVALID_CURSOR` |
| `DELETE /api/posts/{id}` | 200 `{trashed:true, purgeAt}` 또는 `{purged:true}` · 404 · 401 |
| `POST /api/posts/{id}/restore` | 200 `{restored:true}` · 404(휴지통에 없음) |
| `DELETE /api/posts/{id}/permanent` | 204 · 404(휴지통에 없음) |
| `POST /manage/posts/{id}/trash|restore|purge?tab=` | (스크립트 없음) 처리 후 303 `/manage/posts?tab=` |
