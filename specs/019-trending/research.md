# Research: 트렌딩 (019-trending)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/32-trending.md`, 009 목록·010 커서, 016 조회수

사용자 지시: 질문 없이 기본값.

## R-1. 점수 (FR-002~FR-007, FR-017)
- 32 §3-1 SQL을 그대로 쓰되 대상 조건은 006 `publicListingCondition` + `hidden_at IS NULL`, 댓글 작성자 수는 글 작성자·삭제·숨김 제외. 가중치·`+2`·`1.5`·7일·작성자당 3·100개는 설정값. 현재 시각은 `Clock`(테스트 고정).

## R-2. 스냅샷과 넘기기 (FR-008~FR-012)
- `TrendingJob` 10분 `fixedDelay` + 004 `JobLock`. 스냅샷 ID는 UTC `yyyyMMddHHmmssSSS`(같은 분에 두 번 만들어도 겹치지 않게 — 32 §3-2의 분 단위보다 길게), 목록 `trending:{ID}`와 `trending:current` 모두 30분.
- 현재 스냅샷이 없으면(시작 직후) 요청이 한 번 만든다. 빈 순위도 빈 표시 원소 하나로 스냅샷을 남긴다.
- 페이지: `LRANGE` → `cardsByIds`(SQL 1번, 지금 조건) → 빠진 글은 건너뛰고 다음 ID로 9개까지, 커서 `{ID}:{다음 위치}`, 끝이면 없음. 스냅샷 없음 410 `SNAPSHOT_EXPIRED`, 모양 틀림 400 `INVALID_CURSOR`. Redis 실패: 첫 요청은 DB 직접 계산 9개·다음 없음, 커서 요청은 410(처음부터).

## R-3. 화면 (FR-001, FR-013~FR-015)
- `/?tab=trending`, 탭 아래 안내, 빈 상태 "아직 트렌딩 글이 없어요 [최신 글 보기]"(최신으로 채우지 않음). [더 보기] 조각 `moreWith`가 링크 모양(`?tab=trending&cursor=`)과 410 때 갈 주소(`/?tab=trending&expired=1` → "순위가 새로 바뀌었어요")를 받는다. 뒤로 가기 복원은 `list-more.js` 그대로(30분, 주소별 키).

## 구현 메모 (2026-10-08)
- `TrendingIT` 3개(점수·7일·최소 조건·자기 댓글·숨김 댓글·비공개·숨김 글·작성자당 3·동점, 스냅샷 넘기기·재계산 뒤 이어보기·건너뛰기·410·400·화면, 빈 상태·기본 탭), `TrendingFallbackTest`(Redis 장애). 첫 시험에서 작성자당 3개 제한 때문에 기대한 글이 빠지는 테스트 설계 실수를 고쳤다.
