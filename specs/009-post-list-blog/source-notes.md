# Source Notes: 009-post-list-blog

## plan 단계에서 참고할 기술 결정

- API `GET /api/posts?cursor=&size=9`, `GET /api/members/{handle}/posts?cursor=&size=9`, 응답 JSON 형태(`items`, `nextCursor`) (docs/10-post-list.md §4-2)
- 커서: `(first_public_at, id)`를 Base64URL, `WHERE (first_public_at, id) < (:t, :id)`, `LIMIT 10`, 잘못된 커서 `400 INVALID_CURSOR`, SSR은 `/?cursor=…` 링크 (10 §4-2)
- 목록 SQL(글 + 작성자 JOIN 1번, `withdrawn_at IS NULL`, 본문 컬럼 제외) (10 §7)
- 인덱스 `ix_post_feed`, `ix_post_blog` (10 §7, 03-erd.md); 친구 공개 적용 시 `ix_post_blog_friends`, 정렬 `published_at` (06 §6-1·§6-3)
- 공용 목록 조건 `VisibilityFilter` / `PostQueryRepository` (06 §7 R-2·R-2a)
- 카드 CSS: `aspect-ratio: 16/9`, `object-fit: cover`, `line-clamp: 3` + `min-height`, 색 토큰 `--thumb-empty` (#F1F3F5 / #2B2F33), `<time datetime>` (10 §2)
- 뒤로 가기 복원 `sessionStorage` 30분 (10 §4-4, L-6)
- 요약 생성 알고리즘(정화된 HTML에서) — 발행 시 `post.excerpt` (10 §2-1, 05-publish.md §7 ②)
- 썸네일 `post.thumbnail_url`, 저장 키 `{uuid}_thumb.webp` (10 §6)
- 블로그 주소 대문자 → 301 (08-blog-address.md §6)
- 탈퇴 유예 제외 `m.withdrawn_at IS NULL` (13-delete-withdraw.md)
- 성능 기준 300ms·N+1 금지 (02-architecture.md §6, 헌법 비기능 최소선)
