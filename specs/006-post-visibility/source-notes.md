# Source Notes: 006-post-visibility

## plan 단계에서 참고할 기술 결정

- 공개 범위 변경 API `PATCH /api/posts/{postId}/visibility` → 200 `{visibility, firstPublicAt}` / 400 `INVALID_VISIBILITY` / 404 (docs/06-visibility.md §4)
- 변경 처리: 행 잠금(`SELECT … FOR UPDATE`) 후 UPDATE, `first_public_at`은 `CASE WHEN first_public_at IS NULL AND status='PUBLISHED' AND :to='PUBLIC'` (06 §4 SQL, 05-publish.md §3)
- 커밋 후 `PostVisibilityChanged(postId, from, to)` 도메인 이벤트 (06 §4, docs/20-domain-events.md)
- 읽기 판정 단일화 `PostAccessPolicy.canRead(post, viewer)` (06 §7 R-1, 42 §3)
- 목록 공용 조건 `PostQueryRepository` + `VisibilityFilter.forViewer(viewer, author)`; `deleted_at IS NULL` + 작성자 `withdrawn_at IS NULL` 포함 (06 §7 R-2·R-2a)
- `VisibilityRule` 인터페이스(visibility / canRead / listCondition)를 Bean으로 등록, 공통은 PUBLIC·PRIVATE, 적용자는 `FriendsVisibilityRule` 추가 (06 §7 R-3)
- `PostNotFoundException` 하나로 404 통일 (06 §7 R-4)
- 비공개 응답 `Cache-Control: private, no-store` (06 §7 R-5)
- 볼 수 없는 글의 OG 메타 문구와 `robots noindex` (06 §3-1)
- `member.default_visibility` 컬럼, 기본 `PUBLIC` (06 §5, 03-erd.md)
- 친구 공개 규격 스키마: CHECK 교체 + `friendship(member_a_id < member_b_id, requested_by, status PENDING/ACCEPTED, accepted_at)` + `ix_friendship_b`, `ix_post_blog_friends` 부분 인덱스, 마이그레이션 `V{n}__friends.sql`, 동시 요청은 `ON CONFLICT` (06 §6-1, 03-erd.md E-10)
- 친구 판정 쿼리 `LEAST/GREATEST` 한 행 확인 (06 §6-3)
- 관리자 숨김 시 `hidden_at IS NULL`(작성자 제외)을 공용 조건·canRead에 추가 (43-report-hide.md §4)
- 권한 매트릭스 통합 테스트(Testcontainers PostgreSQL) (06 §8, 42 §12)
