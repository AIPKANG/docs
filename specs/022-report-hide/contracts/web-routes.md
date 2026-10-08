# Contract: 신고·숨김·정지 (022)
| 경로 | 결과 |
|---|---|
| `POST /api/reports` `{targetType: POST\|COMMENT, targetId, reason, detail?}` | 201 첫 신고 · 200 재신고 · 400 · 401 · 403 · 404 · 429 |
| `GET /admin/reports?tab=pending\|done`, `GET /admin/reports/{caseId}` | 관리자 화면(비회원 303, 그 밖 404) |
| `POST /admin/reports/{caseId}/hide` (`reason`, 선택 `suspendPeriod`·`suspendReason`·`authorId`) · `/reject` · `/unhide` | 303 |
| `POST /api/admin/reports/{caseId}/hide` `{reason}` · `/reject` · `/unhide` | 204 · 400 · 401 · 404 |
| `GET /admin/members?q=`, `POST /admin/members/{id}/suspend` (`period` 1\|7\|30\|PERMANENT, `reason`) · `/lift` | 화면·303 |
| `POST`·`DELETE /api/admin/members/{id}/suspension` | 204 · 400 · 401 · 404 |
