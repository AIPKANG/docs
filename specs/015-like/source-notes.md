# Source Notes: 015-like

원문: `docs/30-like.md` (§1 K-1~K-11, §2 권한, §3 API, §4 처리·동시성, §5 화면, §6 남용·이벤트, §7 다른 상태, §9 완료 기준, ERD 변경 제안)
교차: `docs/42-permission-matrix.md` §3, §4, §7, P-6, P-9 (응답 코드·판정 순서는 42를 따름)

## plan 단계에서 참고할 기술 결정

- API: `PUT /api/posts/{postId}/like`, `DELETE /api/posts/{postId}/like` → 200 `{ liked, likeCount }`. SSR은 폼 POST `_method=PUT/DELETE` 후 글 상세로 (30 §3)
- 처리 SQL: `WITH ins AS (INSERT … ON CONFLICT DO NOTHING RETURNING post_id) UPDATE post SET like_count = like_count + 1 …`, 취소는 `WITH del AS (DELETE … RETURNING)` (30 §4). 응답 `likeCount`는 같은 트랜잭션에서 `SELECT like_count`
- 카운터는 `@Modifying` 쿼리 (05 J-2), `post_like` 복합 PK, `like_count` 비정규화 (03 E-6·E-7)
- 보정 배치: 매일 새벽 `LEFT JOIN … GROUP BY` 비교 후 UPDATE `RETURNING`, ShedLock, 커지면 최근 7일 대상 (30 §4-1)
- 동시성 검증 결과(PostgreSQL 18 실측) 표 (30 §4-2) — 통합 테스트 시나리오로 재사용
- 요청 제한 Redis 카운터 `rate:like:{memberId}` 1분 60번 → 429 `Retry-After` (30 §6)
- 이벤트 `PostLiked{postId, likerId, authorId, likedAt}`, `PostUnliked{postId, likerId}`, `@TransactionalEventListener(AFTER_COMMIT)` (30 §6, 20)
- 글 상세 "내가 눌렀는지" `EXISTS` 1번(PK 조회), 목록 카드는 수만(목록 SQL 1번 유지, 10 §7) (30 §5, K-8)
- 화면: `<button aria-pressed>`, 0.3초 디바운스 (30 §5)
- ERD 변경 없음. 03 §4에 취소 쿼리 추가, 03 E-6에 "취소도 실제 DELETE 때만 −1, 매일 보정" 추가 (30 ERD 변경 제안)
- 응답 코드 차이: 30 원문은 자기 글 403 / 판정 순서 ②③ 반대 → 42 P-9·§3에 맞춰 400·계정 상태 먼저로 구현 (42 "다른 담당자와 맞출 것")
