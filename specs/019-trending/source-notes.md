# Source notes: 019-trending

원문: `docs/32-trending.md` (참고: `docs/31-view-count.md` W-7, `docs/43-report-hide.md` "다른 담당자와 맞출 것", `docs/20-domain-events.md` §5)

## plan 단계에서 참고할 기술 결정

- 계산 SQL: `scored`(공용 조건 + `first_public_at >= now()-7d`, 댓글 작성자 수 상관 하위 쿼리) → `ranked`(점수·최소 조건) → `capped`(`row_number() OVER (PARTITION BY author_id …)` ≤ 3) → `LIMIT 100` (32 §3-1). 구현 시 06 §7 `VisibilityFilter` 재사용
- 범위 축소는 `ix_post_feed`(`first_public_at`) 사용 (32 §3-1)
- 스냅샷: 10분 주기(ShedLock), `RPUSH trending:{yyyyMMddHHmm}` + `EXPIRE 1800`, `SET trending:current` (32 §3-2, T-7)
- API `GET /api/posts/trending?cursor={스냅샷ID}:{위치}&size=9`, `LRANGE` 후 SQL 1번으로 카드 조회(10 §7 카드 컬럼), `nextCursor` null 처리 (32 §4)
- 스냅샷 만료: `410 SNAPSHOT_EXPIRED` (32 §4)
- Redis 장애: §3-1 직접 실행해 9개, `nextCursor: null` (32 T-9, §4)
- 화면 주소 `/?tab=trending` (32 §5)
- 스키마 변경 없음. 느려지면 `ix_comment_post_author ON comment (post_id, author_id) WHERE deleted_at IS NULL` 추가 (32 ERD 변경 제안)
- 이벤트를 구독하지 않고 카운터 컬럼(`post.like_count`, `view_count`)과 `comment`를 직접 사용 (20 §5 권장, EV-6)
- 성능 목표 p95 200ms (김민서 PERF-5) (32 §7)
- 검증 샘플 13개 글·점수표 (32 §6) — 통합 테스트 데이터로 재사용
- 미결: 숨긴 댓글(`comment.hidden_at`) 제외 요청 (43) → spec FR-017
