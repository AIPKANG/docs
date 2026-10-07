# Research: 임시저장·자동 저장 (004-draft-autosave)

**Phase 0 산출물** · 작성일 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `.specify/memory/constitution.md`, `docs/04-draft-and-image.md` §1~§3·§5·§6-2, `docs/42-permission-matrix.md` §5, `docs/05-publish.md` §7(경계만), `docs/01-common-requirements.md` §4(결정 기록), `docs/07-auth.md`(공용 PC), [003 research](../003-profile/research.md), 현재 `src/` 코드

각 항목은 **Decision / Rationale / Alternatives considered** 형식이다. 001~003에서 정한 기술 결정(SSR·세션, Boot 4.1.1, Redis 요청 제한 `RedisRateLimiter`, `AccountGuard`, 오류 본문 `ErrorResponse`, 테스트 기반)은 그대로 이어받는다. 사용자 지시(2026-10-07): 질문 없이 합리적인 기본값을 정하고 여기에 남긴다.

---

## R-1. 모듈 배치

- **Decision**: 새 **`post` 모듈**(`com.team.blog.post`)을 만든다. 글 행 만들기·편집 내용 읽기·자동/수동 저장·영구 반영·작업본 취소·빈 임시글 정리를 둔다. 새 글의 기본 공개 범위는 account의 공개 Service `AccountSettingsService.defaultVisibility(memberId)`로만 읽는다. 권한은 `AccountGuard.requireWritable`(401/403) + 소유 판정(404)을 Service 첫 줄에서 한다.
- **Rationale**: 헌법 I. 02 §3이 글 모듈을 `post/`로 정했다. 005(발행)·011(내 글 관리)이 같은 모듈을 넓힌다.
- **Alternatives considered**: 자동 저장만 따로 `draft` 모듈 — 편집 버전·작업본이 발행과 같은 규칙을 공유해야 해서(05 §7) 나누면 규칙이 두 곳에 생긴다.

## R-2. 편집 버전의 "현재 값" (FR-016)

- **Decision**: 글의 현재 편집 버전 = `max(Redis 버퍼 version, DB 버전)`. DB 버전은 임시글이면 `post.edit_version`, 발행 글이면 `max(post.edit_version, post_draft.edit_version)`. 현재 내용은 그 버전을 가진 쪽(같으면 Redis)이다. 버전은 **절대 줄지 않는다**: 변경 취소(R-8)는 `post.edit_version`을 그때의 현재 버전으로 올린다.
- **Rationale**: Redis가 장애에서 돌아왔을 때 남아 있던 옛 키가 DB에 직접 쓴 더 새 버전보다 앞서면 안 된다(R-6). 버전이 단조 증가해야 "옛 버전이 새 버전을 덮어쓰지 않는다"는 조건(`edit_version < :version`)이 모든 경로에서 성립한다.
- **Alternatives considered**: 04 §2-3처럼 Redis 키가 있으면 Redis 버전만 기준 — 장애 복구 직후 오래된 키로 판정하는 틈이 생긴다.

## R-3. 서버 버퍼: Redis Hash + Lua (FR-005, FR-006, FR-016)

- **Decision**: 04 §2-3 그대로 `autosave:post:{postId}` Hash(`memberId`, `title`, `contentMd`, `version`, `savedAt`) + Set `autosave:dirty`. 저장 Lua는 `cur = max(HGET version, ARGV dbVersion)`, 키가 있고 `memberId`가 다르면 거부, `baseVersion ≠ cur`면 `{0, cur}`(충돌), 같으면 HSET·`EXPIRE 24h`·`SADD dirty` 후 `{1, cur+1}`. 동시에 같은 출발 버전으로 온 요청은 Redis 단일 스레드 실행으로 하나만 통과한다(SC-006).
- **Rationale**: 확인과 저장이 원자적이어야 한다(FR-016). DB 버전은 Service가 소유 판정 조회 때 함께 읽어 넘긴다(요청마다 PK 조회 1회, 휴지통 판정도 같이 됨).
- **Alternatives considered**: `WATCH/MULTI` — 재시도 루프가 필요하다. DB 행 잠금만 — 서버 트래픽을 DB에 그대로 실어 D-2(빈도를 단계별로 줄인다)를 어긴다.

## R-4. 영구 반영 스케줄러와 실행 잠금 (FR-006, FR-007)

- **Decision**: `AutosaveFlushJob`이 `blog.post.autosave.flush-interval`(1분)마다 실행. 실행 잠금은 **Redis `SET autosave:flush-lock <token> NX PX`**(기본 50초)로 하고 끝나면 토큰이 같을 때만 지운다. 반영 대상은 `SSCAN autosave:dirty`(한 번에 최대 500개)로 읽고, 글마다:
  1. Hash를 읽는다(없으면 dirty에서 뺀다).
  2. DB 반영(한 트랜잭션, 글 하나): 임시글은 `UPDATE post SET title, content_md, edit_version, updated_at WHERE id=? AND status='DRAFT' AND deleted_at IS NULL AND edit_version < :v`. 발행 글은 `INSERT INTO post_draft … SELECT … FROM post WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL AND edit_version < :v ON CONFLICT (post_id) DO UPDATE … WHERE post_draft.edit_version < EXCLUDED.edit_version`.
  3. Lua로 "Hash 버전이 아직 :v이면 `SREM dirty`" — 반영 중에 새 버전이 들어왔으면 dirty에 남아 다음 실행에서 반영된다. Hash는 지우지 않는다(04 §2-4).
  - `content_html`은 만들지 않는다(FR-007).
- **Rationale**: 버퍼가 Redis에 있으므로 잠금도 Redis에 두면 의존성을 늘리지 않는다. Redis가 장애면 반영할 버퍼도 읽을 수 없으니 잠금을 못 얻어 건너뛰는 것이 맞다. 03의 사진 정리 작업처럼 설정값으로 켜고 끈다(테스트는 끄고 직접 부른다).
- **Alternatives considered**: ShedLock(04 §2-4 "ShedLock 등") — Boot 4/Spring 7 호환 버전 확인과 잠금 테이블(공통 ERD 밖) 추가가 필요하다. 이 작업 하나에는 Redis 잠금으로 충분하다. 개인 확장에서 잡이 늘면 ShedLock으로 바꿀 수 있게 잠금은 `JobLock` 인터페이스 뒤에 둔다.

## R-5. 수동 저장 (FR-002, FR-017)

- **Decision**: 수동 저장 = 자동 저장과 **같은 Lua 확인·저장**(요청 제한 없음) → 바로 그 글 하나를 R-4의 2·3단계로 DB에 반영(동기). 응답은 DB 반영 뒤 200 `{version, savedAt}`. 버퍼에는 받아들였지만 DB 반영이 실패하면 503 `SAVE_DELAYED` + `version`을 돌려준다: 내용은 버퍼(AOF)에 있어 다음 반영 주기에 들어가므로, 클라이언트는 `baseVersion = version`으로 맞추고 "서버에 저장됐지만 영구 반영이 늦어지고 있어요"를 보여준다(자기 자신과 충돌하지 않게).
- **Rationale**: 버전 판정을 한 곳(Lua)에만 둔다. DB에 따로 판정하면 자동 저장과 수동 저장이 동시에 올 때 두 기준이 엇갈린다.
- **Alternatives considered**: 수동 저장은 DB 행 잠금으로만 판정 — Redis에 더 새 버전이 있으면 잘못 판정한다.

## R-6. Redis 장애 시 DB 직접 저장 (FR-013, SC-007)

- **Decision**: `AutosaveBuffer` 호출이 Redis 접근 예외(`DataAccessException` 계열: 연결 실패·시간 초과·`OOM command not allowed`)를 던지면 같은 요청을 **DB 경로**로 처리한다: 한 트랜잭션에서 `post`(발행 글이면 `post_draft`도) 행을 `FOR UPDATE`로 잠그고 DB 현재 버전과 `baseVersion`을 비교해 같으면 저장하고 +1, 다르면 409. 간단한 차단기를 둔다: 실패 후 `blog.post.autosave.redis-retry-after`(30초) 동안은 Redis를 건너뛰고 DB 경로로 바로 간다. 요청 제한도 Redis를 쓰므로 그동안은 제한을 건너뛴다(열린 실패, 글 손실 방지가 우선).
- **Rationale**: 04 §2-6 "Circuit Breaker로 DB 직접 저장". 버전 확인을 DB 행 잠금으로 해서 같은 규칙(하나만 성공)을 지킨다. 장애 중 저장은 DB 버전을 올리므로 복구 뒤 남은 옛 Redis 키는 R-2의 `max`로 무시된다.
- **한계(문서화)**: 장애 직전 1분 안에 Redis에만 있던 버전을 출발점으로 한 클라이언트는 DB 버전과 달라 409를 한 번 받는다. 몰래 덮어쓰지 않는다는 원칙 쪽으로 기운 결과이며, 사용자는 비교 창에서 고른다.
- **Alternatives considered**: Resilience4j — 의존성 추가 대비 필요한 기능이 "일정 시간 건너뛰기" 하나다.

## R-7. 요청 제한·본문 크기 (FR-008)

- **Decision**: 자동 저장만 `RedisRateLimiter.tryAcquire("post:autosave:member:{id}", 1, 5s)` → 넘으면 기존 `RateLimitedException`(429 `RATE_LIMITED` + `Retry-After`). 본문 크기는 컨트롤러 진입 전에 `Content-Length`(없으면 읽은 바이트)로 1MB(`blog.post.autosave.max-body-bytes`)를 넘으면 413 `PAYLOAD_TOO_LARGE`. 규칙 값 검사는 Service: 제목 100자(DB `varchar(100)`) 초과 400 `TITLE_TOO_LONG`, 본문 10만 자(DB CHECK) 초과 400 `CONTENT_TOO_LONG`. 수동 저장·새 글은 같은 크기 검사만 한다.
- **Rationale**: 04 §2-1. 요청 제한은 "사용자당"이라 키는 회원 ID. 발행 전 자동 저장은 원문을 그대로 남긴다(정화·제목 정리는 발행 때, 005·007) — 단 제목의 줄바꿈·제어 문자는 저장 때 공백으로 바꾼다(한 줄 칸).
- **Alternatives considered**: 수동 저장도 5초 제한 — 사용자가 직접 누른 저장을 막으면 D-3에 어긋난다.

## R-8. 작업본과 변경 취소 (FR-022~FR-024)

- **Decision**: 발행 글의 저장은 `post`를 건드리지 않고 `post_draft`에만 쓴다(R-4 2단계). 편집 화면을 열 때 현재 내용(R-2)을 보여 주므로 작업본이 있으면 작업본이다. [변경 취소] `DELETE /api/posts/{id}/working-copy`: 한 트랜잭션에서 `post` 행 잠금 → 현재 버전(Redis 포함) 계산 → `post_draft` 삭제 → `post.edit_version = 현재 버전`(R-2 단조 증가, `updated_at`은 그대로) → 커밋 후 Redis 키·dirty 삭제. 반영 작업이 그사이 옛 Hash를 읽었어도 `post.edit_version < :v` 조건 때문에 작업본을 되살리지 못한다. 임시글에 변경 취소를 부르면 404(작업본 개념 없음), 작업본 없는 발행 글은 204(멱등).
- "수정 중" 표시: `post_draft` 행 또는 `post.edit_version`보다 큰 Redis 버퍼가 있으면 수정 중. 내 글 관리 목록은 011 범위지만 다시 열 곳이 필요하므로 최소 목록(R-11)에 이 표시를 넣는다.
- **Rationale**: 04 §2-5·§6 결정 1, 42 §5-2(변경 취소 권한).
- **Alternatives considered**: 취소 때 버전을 되돌림 — 반영 작업과의 경합으로 작업본이 되살아날 수 있다.

## R-9. 빈 임시글 정리 (FR-025, SC-009)

- **Decision**: `EmptyDraftCleanupJob` 매일 04:40(Asia/Seoul, `blog.post.empty-draft-cleanup.cron`), 같은 Redis 잠금 방식. 후보: `status='DRAFT' AND deleted_at IS NULL AND btrim(title)='' AND btrim(content_md)='' AND created_at < now-24h AND updated_at < now-24h`(한 번에 500건). 후보마다 `EXISTS autosave:post:{id}`면 건너뛰고, 아니면 같은 조건을 다시 건 `DELETE … WHERE id=? AND <조건>`으로 지운다. Redis에 닿지 못하면 이번 실행은 건너뛴다(버퍼 존재를 확인할 수 없으므로 지우지 않음).
- **Rationale**: 04 §2-5 + 결정 기록(04가 더 엄격, specs/README). 휴지통 글의 영구 삭제는 011 범위라 제외한다.
- **Alternatives considered**: 공백만 있는 제목을 "비어 있지 않음"으로 보기 — 사용자에게는 빈 글이므로 `btrim`으로 본다.

## R-10. 브라우저 쪽 (FR-004, FR-005, FR-009~FR-012, FR-018~FR-021)

- **Decision**: 의존성 없는 순수 JS 모듈 4개(`static/js/editor/`):
  - `draft-store.js`: IndexedDB 래퍼. DB `blog-drafts`, 저장소 `kv`, 키 `draft:{memberId}:{postId}` 값 `{title, contentMd, baseVersion, dirty, pendingImages, updatedAt}`, 백업 `draft-backup:{memberId}:{postId}` 값 `{…, expiresAt}`(7일). 001의 로그아웃 정리(`auth-logout.js`)는 모든 IndexedDB를 키 접두어로 지우므로 그대로 동작한다. IndexedDB를 못 쓰면(사생활 모드 등) 메모리에만 두고 상태에 그 사실을 적는다.
  - `autosave.js`: 1초 멈춤 → 로컬 저장, 3초 멈춤 또는 마지막 전송 후 30초 → 서버 전송(바뀐 경우만), `visibilitychange(hidden)`·`pagehide` → 즉시 전송(`fetch keepalive`, 64KB를 넘으면 keepalive 없이 시도하고 남은 것은 다음 열기 때 동기화), 탭당 요청 1개, 실패 시 2·4·8…60초 + 0~1초 무작위 지연, 429는 `Retry-After`를 따른다, `online` 이벤트에 즉시 재시도, `beforeunload` 확인창(dirty일 때만). 상태 줄은 FR-011 네 문구를 `textContent`로.
  - `diff.js`: 줄 단위 LCS(Myers) 비교 → 바뀐 줄 쌍은 단어(공백 경계) 단위 LCS로 강조. `−`/`+` 기호와 색을 함께, 바뀌지 않은 6줄 이상 구간 접기, [이전 차이]/[다음 차이]. 좁은 화면(720px 미만)은 위아래로 합친 보기.
  - `editor.js`: 화면 묶기, 열 때 로컬 데이터 판정(FR-019), 충돌 배너·비교 창(`<dialog>`)·세 선택지(FR-021).
- **Rationale**: CSP `script-src 'self'`라 외부 CDN을 쓸 수 없고, 04가 예로 든 `jsdiff`·`localforage`를 저장소에 복사하면 라이선스·갱신 관리가 생긴다. 필요한 기능(줄·단어 LCS, 키-값 저장)은 작다. 003도 의존성 없는 JS로 했다.
- **Alternatives considered**: `jsdiff` 파일을 `static/vendor/`에 둠 — 가능하지만 위 이유로 미룸. localStorage — 04 §2-2가 기각(용량·동기).

## R-11. 화면과 진입점

- **Decision**:
  - `POST /write`(폼, CSRF) → 새 임시글 → 303 `/write/{postId}`. 머리글에 [새 글] 버튼(로그인 시).
  - `GET /write/{postId}` → 편집 화면(제목 입력, Markdown 본문 `textarea`, 상태 줄, [저장], 발행 글이면 [변경 취소]). 서버 현재 내용(R-2)을 `data-*`가 아닌 `<script type="application/json">`(Thymeleaf `th:inline` 없이 `th:text`로 JSON 문자열 이스케이프)로 넘긴다.
  - `GET /manage/posts` → 최소 "내 글" 목록(제목 또는 "(제목 없음)", 배지 임시저장/발행/수정 중, 마지막 수정 시각, 편집 링크). 탭·페이지·휴지통·삭제는 011이 넓힌다.
  - 발행 버튼은 005가 더한다. 미리보기(렌더링)는 005·007.
- **Rationale**: "다른 날 다시 열어 이어 쓴다"(US1-5)를 화면에서 확인하려면 다시 열 곳이 있어야 한다. 011 전체를 당겨오지 않고 최소만 둔다.
- **Alternatives considered**: `GET /write`가 바로 행을 만듦 — GET에 부작용이 생기고 링크 미리보기·새로고침이 임시글을 만든다.

## R-12. 오류 응답 (FR-014, FR-017)

- **Decision**: 409는 `ErrorResponse`에 선택 필드 `server`를 더해 `{ code: "EDIT_CONFLICT", message, server: { title, contentMd, version, savedAt } }`. 404 `NOT_FOUND`(없음·남의 글·휴지통 동일 본문), 401 `LOGIN_REQUIRED`, 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`, 429 `RATE_LIMITED`, 413 `PAYLOAD_TOO_LARGE`, 400 `TITLE_TOO_LONG`/`CONTENT_TOO_LONG`/`INVALID_REQUEST`(`baseVersion` 누락·음수).
- **Rationale**: 42 §4 이유 코드 형식 유지, 필드 추가만(기존 응답 불변).

## R-13. 테스트 전략 (헌법 VI)

- **Decision**: 통합 테스트(실제 PostgreSQL·Redis): 새 글·권한 표(42 §5-2) 전체, 자동 저장·충돌·동시 20건(SC-006), 반영 작업(임시글/작업본/옛 버전 무시/반영 중 새 버전), 수동 저장, Redis 장애 경로(Redis 버퍼를 실패하는 구현으로 바꾼 별도 컨텍스트), 요청 제한·413, 작업본·독자 화면(발행 상태는 SQL로 만든다 — 발행은 005), 변경 취소 경합, 빈 임시글 정리 조건별, 편집 화면 HTML. 단위 테스트: 버전·제목 규칙. 브라우저 JS(타이머·IndexedDB·diff)는 자동 테스트 기반이 없어 quickstart 수동 확인 + `diff.js`의 순수 함수는 Node로 돌릴 수 있는 작은 자체 검사 스크립트(`src/test/js/diff.test.mjs`, `node`만 필요, Gradle 빌드에는 넣지 않음).
- **Rationale**: 권한·소유·동시성은 통합 테스트 필수(헌법 VI). JS 테스트 도구(npm)를 빌드에 들이지 않는다.

---

## 남은 확인 사항 (계획 진행을 막지 않음)

| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 실행 잠금을 Redis로 한 것(04는 ShedLock 예시) | Redis `SET NX PX`. 팀이 ShedLock을 원하면 `JobLock` 구현만 바꾼다 |
| U-2 | Redis 장애 중 최근 1분 버전 출발 클라이언트의 409 한 번 | 허용(덮어쓰기 방지 우선) |
| U-3 | 최소 "내 글" 목록을 004에서 먼저 만듦 | 011이 같은 경로를 넓힌다 |
| U-4 | `jsdiff`·`localforage` 대신 자체 구현 | 자체 구현(R-10) |
| U-5 | 수동 저장의 DB 반영 실패 시 503 `SAVE_DELAYED` + `version` | R-5 |
