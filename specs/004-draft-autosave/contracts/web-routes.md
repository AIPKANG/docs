# Contract: 화면·HTTP 경로 (임시저장·자동 저장)

**표현 방식**: Thymeleaf SSR + 세션 쿠키. 편집 화면의 저장은 JS(`static/js/editor/*.js`)가 아래 JSON API를 부른다. REST 표현 계층을 쓰는 팀원은 같은 Service([post-edit-service.md](./post-edit-service.md))를 부르고 결과를 그대로 옮긴다.

공통 규칙
- 상태를 바꾸는 요청은 CSRF 헤더(`X-CSRF-TOKEN`) 필수. 경로에 회원 식별자 없음(헌법 III).
- 권한(42 §5-2): 비회원 401(SSR은 `/login?redirect=…` 303), 인증 전 403 `EMAIL_NOT_VERIFIED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 남의 글·없는 글·휴지통 글 404(같은 본문). 관리자도 남의 글은 404.

## 1. 화면

| 메서드·경로 | 결과 | 요구사항 |
|---|---|---|
| `POST /write` | 새 임시글(`edit_version` 0, 공개 범위 = 회원 기본값) → 303 `/write/{postId}` | FR-015, US1-1 |
| `GET /write/{postId}` | 편집 화면: 제목·본문(현재 내용, research R-2), 상태 줄, [저장], 발행 글이면 "수정 중" 안내와 [변경 취소], 충돌 배너·비교 창 자리. 서버 내용과 저장 간격은 `#editor[data-state]` 속성 하나에 JSON으로(속성 값은 Thymeleaf가 이스케이프) | FR-019, FR-023 |
| `GET /manage/posts` | 내 글 최소 목록: 제목(없으면 "(제목 없음)"), 배지 `임시저장`/`발행`/`수정 중`, 마지막 수정 시각, 편집 링크, [새 글] | FR-023 |

머리글(로그인 시): [새 글](POST /write 폼), [내 글](/manage/posts).

## 2. JSON API

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/posts` | `{ "title"?: string, "contentMd"?: string }` | 201 `{ postId, version: 0 }` — "새 임시글로 따로 저장"도 이 경로 | 400, 401, 403, 413 |
| `GET /api/posts/{postId}/editing` | — | 200 `{ postId, status, title, contentMd, version, savedAt, editing }` | 401, 403, 404 |
| `PUT /api/posts/{postId}/autosave` | `{ title, contentMd, baseVersion }` | 200 `{ version, savedAt }` | 409 `EDIT_CONFLICT` + `server`, 404, 400, 413, 429(+`Retry-After`), 401, 403 |
| `PUT /api/posts/{postId}/draft` (수동 저장) | 같음 | 200 `{ version, savedAt }` (DB 반영까지) | 409, 404, 400, 413, 503 `SAVE_DELAYED` + `version`, 401, 403 |
| `DELETE /api/posts/{postId}/working-copy` (변경 취소) | — | 204 (작업본 없는 발행 글도 204) | 404(임시글 포함), 401, 403 |

409 본문 예:

```json
{ "code": "EDIT_CONFLICT", "message": "다른 탭이나 기기에서 이 글이 수정되었어요.",
  "server": { "title": "JPA N+1 정리", "contentMd": "## 문제\n...", "version": 13, "savedAt": "2026-10-07T05:03:12Z" } }
```

## 3. 브라우저 동작 계약 (수동 확인, quickstart S1~S3)

| 동작 | 규칙 |
|---|---|
| 로컬 저장 | 입력이 1초 멈추면 `draft:{memberId}:{postId}`에 `dirty=true`로 |
| 서버 전송 | `dirty`이고 (3초 멈춤 \| 마지막 전송 후 30초 \| 탭 숨김 \| `pagehide`) — 탭당 요청 1개 |
| 응답 200 | `baseVersion = version`, 보낸 뒤 바뀐 게 없으면 `dirty=false`, "✓ 저장됨 HH:MM" |
| 실패·오프라인 | 2→4→8…60초(+무작위 0~1초), 429는 `Retry-After`. "⚠ 오프라인 — …" 또는 "● 이 기기에 저장됨 (동기화 대기)" |
| 409 | 전송 멈춤, 로컬 저장 계속, 배너 "⚠ 다른 탭이나 기기에서 이 글이 수정되었어요(HH:MM). 지금 내용은 이 기기에만 저장되고 있어요. [비교하기]", 상태 "⚠ 다른 곳에서 수정됨 — 이 기기에만 저장 중 [비교하기]" |
| 비교 창 | [편집 중인 내용으로 저장](확인 문구 후 `baseVersion = server.version`으로 수동 저장) / [저장된 내용 불러오기](백업 7일, 에디터 교체) / [새 임시글로 따로 저장](`POST /api/posts` 후 새 글로 이동, 원래 글 로컬 데이터 정리) / 닫기 |
| 열 때 | 로컬 `dirty` 없음 → 서버 내용. `dirty`이고 `baseVersion == 서버 version` → 로컬 내용 + "이 기기에 저장되지 않은 변경을 불러왔어요". 다르면 비교 창 즉시 |
| 떠날 때 | `dirty`면 `beforeunload` 확인창. 동기화된 상태로 떠나면 로컬 키 삭제 |
