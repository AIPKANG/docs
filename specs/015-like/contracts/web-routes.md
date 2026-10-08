# Contract: 좋아요 (015)
| 경로 | 결과 |
|---|---|
| `PUT /api/posts/{id}/like` | 200 `{liked:true, likeCount}` · 400 · 401 · 403 · 404 · 429 |
| `DELETE /api/posts/{id}/like` | 200 `{liked:false, likeCount}` · 같음 |
| `POST /@{handle}/posts/{id}/like` (`liked=true|false`, 폼) | 303 상세 |
