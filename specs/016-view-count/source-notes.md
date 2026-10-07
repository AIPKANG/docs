# Source notes: 016-view-count

원문: `docs/31-view-count.md` (참고: `docs/40-post-detail.md` R-7·R-8·§4, `docs/42-permission-matrix.md` "다른 담당자와 맞출 것")

## plan 단계에서 참고할 기술 결정

- 기록 API `POST /api/posts/{postId}/views`, 응답 204/404/429, CSRF 토큰 필요 (31 §4-2)
- 브라우저 스크립트: `visibilitychange` + 1초 `setTimeout`, `fetch(keepalive)`, 재전송 없음 (31 §4-1). CSP 때문에 외부 파일 `/js/post-view.js` + `data-` 속성으로 글 ID·CSRF 전달 요청 (40 "다른 담당자와 맞출 것")
- 방문자 키: `m:{memberId}` / `v:{vid 쿠키}`(UUID, 1년, HttpOnly·Secure·SameSite=Lax) / `h:{SHA-256(IP+UA+일별 비밀값)}` (31 §2-1)
- 설정값 `blog.view.dedupe-window`(24h), `blog.view.max-per-window`(1); 봇 UA 목록은 설정 파일 (31 §2-2, §3)
- 미리 불러오기 판정: `Purpose: prefetch` / `Sec-Purpose: prefetch` 헤더 (31 §3)
- 읽기 판정은 `PostAccessPolicy.canRead` + `PUBLISHED` 확인 (31 §3, 06 R-1)
- Redis Lua 스크립트: `INCR view:seen:{postId}:{visitor}` + 최초 `EXPIRE`, 한도 이내면 `HINCRBY view:pending:{yyyyMMdd KST}` (31 §5-1). `SET NX` 대신 `INCR`인 이유는 31 §9 주석
- 1분 주기 반영(ShedLock): `RENAME` → `:processing:{시각}`, 남은 처리 중 키 우선 처리, 글마다 한 트랜잭션(`@Modifying` UPDATE `view_count` + `post_view_daily` UPSERT) 후 `HDEL` (31 §5-2, 05 J-2). `updated_at` 변경 금지
- Redis 장애 시 기록 건너뛰고 204 (31 §5-1, W-6)
- `post_view_daily(post_id, view_date, views)` PK `(post_id, view_date)`, `ON DELETE CASCADE`, 인덱스 `(view_date, post_id) INCLUDE (views)`, `CHECK (views > 0)` (31 §6, ERD 변경 제안). 03에 E-24 추가안
- 90일 보관 배치: 매일 새벽 `view_date < 오늘-90일` 삭제 (31 §6)
- 검증 기록: Redis 7 + PostgreSQL 18 테스트 12개 (31 §9) — 통합 테스트 시나리오로 재사용
- 미결: 관리자 조회 제외 (40 R-8, 42 요청) → spec FR-019
