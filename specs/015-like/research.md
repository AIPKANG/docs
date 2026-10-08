# Research: 좋아요 (015-like)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/30-like.md`, `docs/42-permission-matrix.md` §3·§7·P-9, 014 구현(`interaction` 모듈·`PostReadAccess`·`PostCounters`)

사용자 지시: 질문 없이 기본값. 30 원문의 "자기 글 403·판정 순서"는 42와 결정 기록(2026-10-07: 400)에 맞춘다.

## R-1. 처리 (FR-001~FR-012)
- `LikeService.set(user, postId, liked)`: `requireWritable`(401/403) → `PostReadAccess.requireWritableTarget`(404) → 자기 글 400 `CANNOT_LIKE_OWN_POST` → 1분 60번(좋아요·취소 합산, 429) → 트랜잭션: `INSERT … ON CONFLICT DO NOTHING` 또는 `DELETE`, 바뀐 행이 1일 때만 `PostCounters.adjustLikes(±1)`과 사건 `PostLiked`/`PostUnliked`, 응답 `{liked, likeCount}`(같은 트랜잭션의 최신 수). PK `(post_id, member_id)`로 동시 요청도 1건.
- 매일 04:50 보정: `PostCounters.reconcileLikes()`(실제 기록 수와 다른 글만 UPDATE, 고친 수 > 0이면 경고), 004 `JobLock`.

## R-2. 화면 (FR-013~FR-020)
- 상세 확장점 `LikeDetailSection`이 "내가 눌렀는지"(PK 조회 1번)와 숫자(010 `ViewCountFormat` — 같은 1,234/1.2만 규칙). 작성자에게는 수만, 나머지(비회원·인증 전 포함)에게는 `<button aria-pressed aria-label="좋아요 (N)">♡/♥ N</button>`을 폼 안에 둔다(스크립트 없으면 `POST /@주소/posts/{id}/like` → 303 상세). `like.js`: 바로 바꾸고 0.3초 디바운스로 마지막 상태만 PUT/DELETE, 실패하면 되돌림 + "좋아요를 반영하지 못했어요", 비회원은 "로그인하고 좋아요를 눌러 보세요 [로그인]"(자동으로 누르지 않음), 인증 전은 인증 안내. 목록 카드는 수만(009 그대로).

## R-3. 다른 상태 (FR-021~FR-023)
- 비공개·휴지통·숨김에도 기록·수 보존(행을 지우지 않음), 볼 수 없는 사람은 상세 자체가 404. 완전 삭제는 FK CASCADE(011). 누른 사람의 탈퇴 유예는 영향 없음.

## 구현 메모 (2026-10-08)
- `LikeIT` 5개(멱등·사건, 동시 20요청 10명 → 정확히 10, 권한·순서·자기 글·제한, 보존·보정, 상세 상태·폼), 012 매트릭스 표 4 행. Docker 컨테이너 시작 지연으로 한 번 전체 실패했다가 다시 실행해 통과(환경 문제, 코드 무관).
