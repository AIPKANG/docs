# Contract: 조회수 (016)
| 경로 | 결과 |
|---|---|
| `POST /api/posts/{id}/views` | 204(셌든 안 셌든) · 404(볼 수 없는 글) · 429(같은 방문자 1분 60번 초과) |
| `GET /@{handle}/posts/{id}` | 비회원·쿠키 없음이면 `Set-Cookie: vid=…; Max-Age=31536000; HttpOnly; SameSite=Lax`, "조회 N"과 안내, 작성자가 아니면 `post-view.js` |
