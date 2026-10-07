# Data Model: 임시저장·자동 저장 (004)

**Phase 1 산출물** · 작성일 2026-10-07 · 근거: `V1__common_schema.sql`(공통 ERD), [research.md](./research.md)

**새 마이그레이션 없음.** V1의 `post`·`post_draft`에 필요한 컬럼(`edit_version` 등)이 이미 있다(헌법 II).

---

## 1. PostgreSQL

### `post` (V1, 이 기능이 쓰는 컬럼)

| 컬럼 | 이 기능에서의 의미 |
|---|---|
| `id` | 글 식별자. [새 글] 때 만든다 |
| `author_id` | 작성자. 인증 정보에서만 정한다 |
| `title` `varchar(100)` | 임시글의 영구 저장 제목. 발행 글은 마지막 발행본 제목(이 기능이 바꾸지 않음) |
| `content_md` `text`(≤10만 자) | 임시글의 영구 저장 본문. 발행 글은 마지막 발행본 |
| `status` | `DRAFT` / `PUBLISHED`(발행은 005) |
| `visibility` | 새 글 = 회원 기본 공개 범위(FR-015) |
| `edit_version` | 임시글: 영구 저장된 편집 버전. 발행 글: 발행 때 버전 → 변경 취소 때 현재 버전으로 올림(R-8). 줄지 않는다 |
| `created_at`, `updated_at` | 빈 임시글 정리 조건. 임시글 반영·수동 저장 때 `updated_at` 갱신 |
| `deleted_at` | 휴지통(011). 값이 있으면 저장·열기 404 |

### `post_draft` (V1, 발행 글의 작업본, 0..1)

| 컬럼 | 의미 |
|---|---|
| `post_id` (PK, FK → post, CASCADE) | 발행 글 |
| `title`, `content_md` | 고치는 중인 내용 |
| `edit_version` | 작업본 버전. 항상 `post.edit_version`보다 크다 |
| `created_at`, `updated_at` | 처음 저장·마지막 반영 시각 |

**상태 전이 (편집 관점)**

```
[새 글] → DRAFT(v0) ──자동/수동 저장──▶ DRAFT(v n)        (post에 반영)
PUBLISHED(v p) ──저장──▶ PUBLISHED + post_draft(v n > p)   (독자는 post를 봄)
PUBLISHED + post_draft ──변경 취소──▶ PUBLISHED(v = 현재 버전), post_draft 없음
PUBLISHED + post_draft ──다시 발행(005)──▶ PUBLISHED(post에 반영), post_draft 없음
DRAFT, 빈 제목·본문, 24h+ 경과, 버퍼 없음 ──정리──▶ 완전 삭제
```

## 2. Redis

| 키 | 형식 | 내용 | 수명 |
|---|---|---|---|
| `autosave:post:{postId}` | Hash | `memberId`, `title`, `contentMd`, `version`, `savedAt`(ISO-8601 UTC) | 저장마다 24h로 갱신(`blog.post.autosave.buffer-ttl`) |
| `autosave:dirty` | Set | 아직 영구 반영하지 않은 postId | 반영 뒤 버전이 그대로면 제거 |
| `autosave:flush-lock` | String | 반영 작업 실행 잠금 토큰 | `PX` 50초 |
| `post:empty-draft-cleanup-lock` | String | 정리 작업 실행 잠금 토큰 | `PX` 10분 |
| `post:autosave:member:{memberId}` | String(카운터) | 자동 저장 요청 제한(5초 1회) | 5초 |

Redis 설정(compose, 001부터): AOF `appendfsync everysec`, `maxmemory-policy noeviction`.

## 3. 브라우저 IndexedDB (`blog-drafts` DB, `kv` 저장소)

| 키 | 값 |
|---|---|
| `draft:{memberId}:{postId}` | `{ title, contentMd, baseVersion, dirty, pendingImages: [], updatedAt }` |
| `draft-backup:{memberId}:{postId}` | `{ title, contentMd, baseVersion, savedAt, expiresAt }` — "저장된 내용 불러오기" 때 7일 백업 |

인증 정보는 넣지 않는다. 로그아웃 때 그 회원 접두어 키 전부 삭제(001 `auth-logout.js`).

## 4. 애플리케이션 값

| 이름 | 필드 | 쓰임 |
|---|---|---|
| `EditingContent` | `postId, status(DRAFT/PUBLISHED), title, contentMd, version, savedAt, editing(작업본 여부)` | 편집 화면·`GET /api/posts/{id}/editing`·409 본문 |
| `SaveCommand` | `title, contentMd, baseVersion` | 자동·수동 저장 입력 |
| `SaveResult` | `version, savedAt` | 저장 성공 |
| `EditConflictException` | `EditingContent server` | 409 |
| `BufferedContent` | `memberId, title, contentMd, version, savedAt` | Redis Hash 읽기 |

## 5. 오류 코드

| 코드 | HTTP | 언제 |
|---|---|---|
| `LOGIN_REQUIRED` | 401 | 비회원 |
| `EMAIL_NOT_VERIFIED` / `ACCOUNT_WITHDRAWN` | 403 | 인증 전 / 탈퇴 유예 |
| `NOT_FOUND` | 404 | 없음·남의 글·휴지통, 임시글에 변경 취소 |
| `EDIT_CONFLICT` | 409 | 출발 버전이 현재 버전과 다름 (+`server`) |
| `TITLE_TOO_LONG` / `CONTENT_TOO_LONG` / `INVALID_REQUEST` | 400 | 제목 100자·본문 10만 자 초과 / `baseVersion` 없음·음수 |
| `PAYLOAD_TOO_LARGE` | 413 | 요청 본문 1MB 초과 |
| `RATE_LIMITED` | 429 | 자동 저장 5초 1회 초과 (+`Retry-After`) |
| `SAVE_DELAYED` | 503 | 수동 저장이 버퍼에 들어갔지만 DB 반영 실패 (+`version`) |

## 6. 설정값 (`blog.post.*`, 헌법 II)

| 키 | 초기값 |
|---|---|
| `autosave.buffer-ttl` | 24h |
| `autosave.flush-interval` | 1m |
| `autosave.flush-batch-size` | 500 |
| `autosave.flush-enabled` | true (테스트 false) |
| `autosave.rate-limit-window` | 5s |
| `autosave.max-body-bytes` | 1048576 |
| `autosave.redis-retry-after` | 30s |
| `editor.local-save-delay` / `server-save-delay` / `server-save-max-interval` | 1s / 3s / 30s (화면에 `data-*`로 전달) |
| `editor.retry-max` | 60s |
| `editor.backup-ttl` | 7d |
| `title-max-length` / `content-max-length` | 100 / 100000 |
| `empty-draft-cleanup.enabled` / `cron` / `min-age` / `batch-size` | true / `0 40 4 * * *` / 24h / 500 |
