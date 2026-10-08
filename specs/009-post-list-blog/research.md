# Research: 전체 글 목록·개인 블로그 (009-post-list-blog)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/10-post-list.md`, `docs/06-visibility.md` §7, 005·006·008 구현

사용자 지시: 질문 없이 기본값. 친구 공개(FR-021)는 006에서 구현하지 않았으므로 해당 없음.

## R-1. 조회 위치·SQL (FR-001~FR-004, FR-023)
- **Decision**: `post.application.PostListQuery`(post가 `post` 테이블을 소유)가 목록 한 번에 SQL 1번: `post p JOIN member m` + `PostAccessPolicy.publicListingCondition("p","m")`(006) + 커서 `(p.first_public_at, p.id) < (?, ?)` + `ORDER BY p.first_public_at DESC, p.id DESC LIMIT 10`, 카드 칸만(본문 컬럼 제외). 10개면 9개만 주고 `nextCursor`. 블로그는 `AND p.author_id = ?`(작성자 본인이 봐도 같은 조건 → 비공개 안 보임). 인덱스 `ix_post_feed`·`ix_post_blog`의 부분 조건과 같은 식이라 그대로 탄다. 화면 컨트롤러는 discovery(02 §3), 작성자 정보는 같은 JOIN에서 읽는다(목록 성능 예외 — account 공개 Service를 글마다 부르면 N+1).
- 공개 글 수(FR-016): 같은 조건 `count(*)`(보는 사람과 무관 — 친구 공개가 없어서).

## R-2. 커서 (FR-004, FR-007)
- **Decision**: `{first_public_at 마이크로초 epoch}_{id}`를 Base64URL(패딩 없음). 해석 실패·음수·형식 밖은 400 `INVALID_CURSOR`. SSR은 `/?cursor=…`, `/@주소?cursor=…` 링크.

## R-3. 카드·날짜 (FR-010~FR-014)
- **Decision**: 템플릿 조각 `fragments/post-card.html`(홈·블로그 공용, 블로그는 작성자 영역 숨김). 날짜: 1시간 이내 "N분 전"(0이면 "방금 전"), 24시간 이내 "N시간 전", 이후 `yyyy.MM.dd`(한국 시간), `<time datetime>`. JS로 붙이는 카드도 같은 규칙. 썸네일 `<img alt=제목 loading=lazy>`, 없으면 `--thumb-empty` 영역. 카드 전체 링크는 제목 링크를 늘린 영역(`::after`)으로, 작성자 링크는 그 위에 따로(키보드 링크 2개).

## R-4. [더 보기]·복원 (FR-005, FR-006, FR-008, FR-009)
- **Decision**: `static/js/post/list-more.js`: [더 보기](`<a href="?cursor=…">`)를 가로채 `GET /api/posts?cursor=…`(블로그는 `/api/members/{handle}/posts`)로 9개를 받아 카드를 만들어 붙인다(글자는 `textContent`). 이미 있는 글 번호는 건너뜀. 불러오는 중 "불러오는 중…" 비활성, 실패 "불러오지 못했어요 [다시 시도]", 끝 "모든 글을 다 봤어요". 떠날 때 `sessionStorage`에 카드 데이터·다음 커서·스크롤을 30분 보관하고, 같은 주소로 돌아오면 복원.

## R-5. API (contracts)
- `GET /api/posts?cursor=` → `{items:[{id,url,title,excerpt,thumbnailUrl,firstPublicAt,commentCount,likeCount,author:{handle,nickname,profileImageUrl}}], nextCursor}`. `size`는 무시(9 고정). `GET /api/members/{handle}/posts?cursor=` — 없는·탈퇴 주소 404.

## R-6. 빈 상태 (FR-020, FR-022)
- 홈 빈 상태: 로그인 "아직 올라온 글이 없어요. 첫 글의 주인공이 되어 보세요 [글쓰기]", 비회원은 [로그인]. 블로그: 남 "아직 공개한 글이 없어요", 본인 "첫 글을 써 보세요 [글쓰기]".

## R-7. 테스트
- 목록 조건·정렬·9개·커서 경계·중복/누락 없음(도중 새 글·비공개·삭제), 잘못된 커서 400, 블로그 본인에게도 비공개 안 보임, 탈퇴 작성자 제외, 공개 글 수, 쿼리 수(Hibernate 통계 대신 JDBC 호출 수: 목록당 1 SELECT), SSR 첫 페이지·`?cursor` 링크, 빈 상태 문구, 날짜 형식 단위 테스트.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 홈 목록 공유 캐시 | 안 함(Spring Security no-store 그대로) |
