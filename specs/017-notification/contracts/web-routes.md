# Contract: 인앱 알림 (017)
| 경로 | 결과 |
|---|---|
| `GET /api/notifications/unread-count` | 200 `{count}` `Cache-Control: no-store` · 401 |
| `GET /api/notifications?cursor=&size=` | 200 `{items[], nextCursor?}`(size 최대 20) · 400 `INVALID_CURSOR` · 401 |
| `PATCH /api/notifications/{id}/read` | 204 · 401 · 404(남의 것, 관리자 포함) |
| `POST /api/notifications/read-all` | 200 `{updated}` · 401 |
| `DELETE /api/notifications/{id}` | 204 · 401 · 404 |
| `GET`·`PUT /api/me/notification-settings` | 200 `{COMMENT, REPLY, LIKE, FOLLOW, NEW_POST}`(모르는 키 무시) · 401 |
| `GET /notifications` | 전체 페이지(비회원 303 로그인) |
| `POST /notifications/{id}/open` · `/read-all` · `/{id}/delete`, `POST /settings/notifications` | 303 |

항목: `{id, type, read, updatedAt, timeLabel, actor?, othersCount?, post?{title,url}|{unavailable:true}, commentPreview?, result?, message, url?}`.
