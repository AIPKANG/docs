# Research: 조회수 (016-view-count)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/31-view-count.md`, 006 `PostReadAccess`, 010 `ViewCountFormat`

사용자 지시: 질문 없이 기본값.

## R-1. 기록 (FR-001~FR-008, FR-017, FR-019)
- `ViewRecorder.record`: `requireReadable`(+숨김 → 404) → 미리 불러오기(`Purpose`/`Sec-Purpose: prefetch`)·봇(User-Agent 목록, 대소문자 무시) → 작성자·관리자 → 방문자 키 → 1분 60번(429) → Lua `INCR view:seen:{글}:{방문자}`(처음이면 `PEXPIRE` 기간), `≤ max`이면 `HINCRBY view:pending:{KST yyyyMMdd} 글 1`. 셌든 안 셌든 204(SC-006).
- 방문자 키: `m:{회원}`, `v:{sha256(쿠키)}`, `h:{sha256(IP|UA + 그날 무작위 비밀값)}`. 비밀값은 메모리에만 두고 KST 날짜가 바뀌면 새로 만든다(서버마다 다를 수 있어 쿠키 없는 비회원은 서버 수만큼 더 셀 수 있음 — 허용). 원래 IP·쿠키 값은 Redis 키·로그에 없다.
- 쿠키 `vid`(UUID, HttpOnly, SameSite=Lax, 1년)는 비회원이 공개 글 상세를 열 때 없으면 발급.

## R-2. 반영 (FR-009~FR-015)
- `ViewFlusher.flush()`: 남은 `view:processing:*`(이전 반영이 죽은 흔적)를 먼저 처리하고, `RENAME view:pending:{날짜} → view:processing:{날짜}:{ms}` 뒤 글마다 트랜잭션 `UPDATE post SET view_count = view_count + n`(updated_at 그대로) + `post_view_daily` UPSERT, 커밋 뒤 `HDEL`. 커밋 직후 HDEL 전 장애 때만 글 하나 1회 중복(스펙 허용).
- `ViewJobs`: 1분 `fixedDelay` 반영, 매일 05:00 90일 지난 일별 기록 삭제, 둘 다 004 `JobLock`. 글 완전 삭제는 FK CASCADE.

## R-3. 화면 (FR-016, FR-006)
- 상세 "조회 N"(010 형식)과 `title`/안내 문구(`blog.view.notice`). 작성자 외 공개 상세에 `post-view.js`: 보이는 동안 1초 타이머(가려지면 멈춤), `fetch keepalive` 한 번. 목록 카드에는 조회수 없음(009 그대로).

## 남은 확인
- ~~U-1: FR-018 처리방침 화면 없음~~ → 해결(2026-10-08, chan 승인): `/privacy` 초안에 "조회수 중복 방지용 무작위 식별자" 안내, 모든 화면 아래·가입 화면 링크.

## 구현 메모 (2026-10-08)
- `ViewCountIT` 6개(중복·만료 후 재집계·일별, 제외 대상·404, 동시 50요청 → 1, 반영·`updated_at` 유지·처리 중 키 복구·90일 삭제, 쿠키·안내·스크립트, 429), `ViewRecorderTest`(Redis 실패 → 건너뜀). 테스트에서 `User-Agent`를 두 번 넣으면 첫 값이 쓰여 봇 판정이 안 되는 실수를 고쳤다(코드 무관).
