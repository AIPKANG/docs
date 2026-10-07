# Source Notes: 010-post-detail

## plan 단계에서 참고할 기술 결정

- 라우팅 순서 `GET /@{handle}/posts/{id}` ①~⑥, 임시글 `302 → /write/{id}` (docs/40-post-detail.md §3, R-3·R-4)
- 판정: `PostAccessPolicy.canRead` (06 §7 R-1, 42 §3), 404 `PostNotFoundException` 통일 (06 R-4)
- 캐시: `Cache-Control: private, no-cache`, 비공개 `no-store` (40 R-9, 06 R-5)
- 메타 태그 템플릿(`<title>`, description, canonical, og:*, article:published_time / modified_time) (40 §5), 볼 수 없는 글 OG 문구 (06 §3-1)
- 조회 기록: `POST /api/posts/{id}/views`, `/js/post-view.js` `<script src defer>`, 글 ID·CSRF 토큰은 `data-` 속성 (40 §4, 31-view-count.md §4-1·W-3·W-4)
- GIF 재생 `/js/gif-play.js` + ▶ CSS (23-image.md §5-2)
- 코드 강조 highlight.js, 코드 블록 있을 때만 로드 (12-content-sanitize.md §7-2)
- 쿼리 수 최대 5번 + 댓글 첫 20개(21-comment.md §6, CM-3, `around`) (40 §6)
- 태그 `post_tag.position` 순, `/tags/{인코딩된 이름}` (22-tag.md §9)
- 좋아요 여부 조회 (30-like.md), 팔로우 버튼 상태 (24-follow-feed.md §2-1), 신고 (43-report-hide.md)
- 사용 컬럼: `first_public_at`, `published_at`, `edited_at`, `excerpt`, `content_html`, `post_draft`; `content_md`는 읽지 않음. ERD 변경 없음 (40 §6, ERD 변경 제안)
- 권한 매트릭스 통합 테스트 (42 §5-1, §11, §12)
- 미해결: 31 W-3에 관리자 제외 추가 요청 (40 "다른 담당자와 맞출 것")
