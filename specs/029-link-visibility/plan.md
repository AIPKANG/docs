# Plan: 링크 공개 — 강성찬 개인 확장

- 새 모듈 `share`: `LinkShareService`(열쇠 만들기·바꾸기·비교), `LinkVisibilityRule`(요청의 `key` 또는 같은 출처 Referer의 `key`가 맞으면 읽기, 공용 목록 조건 없음, `@ConditionalOnProperty`), `LinkShareDetailSection`(글쓴이에게 공유 주소), `LinkShareController`(POST `/posts/{id}/share-link`).
- `V4__link_share.sql`: `ck_post_visibility`에 LINK, `post_link_share(post_id PK, token UNIQUE)`.
- 발행 검사(`PublishValidator`)가 공개 범위 규칙 Bean이 있는 값을 모두 받게(025 친구 공개도 발행 화면에서 고를 수 있게 함께 고침).
- 화면: 발행 설정·글 상세 선택지, 내 글 관리 🔗 표시·필터, 글쓴이 공유 상자.
