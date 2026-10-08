# Contract: 회원 탈퇴 (023)
| 경로 | 결과 |
|---|---|
| `GET /settings/withdraw` | 탈퇴 화면(비회원 303 로그인) |
| `POST /settings/withdraw` (`confirm`, `verification`) | 303 `/withdrawn`(이 기기 로그아웃) · 400 · 409 · 429 |
| `POST /api/me/withdrawal` `{confirm, verification}` | 200 `{restoreDeadline}` · 400 · 401 · 409 `ADMIN_CANNOT_WITHDRAW` · 429 |
| `GET /withdrawn` | 완료 화면 |
| `GET /account/restore` | 복구 화면(복구 전용 세션만, 기한·남은 일수) |
| `POST /account/restore` | 303 `/`(복구, "다시 오신 걸 환영해요") |
