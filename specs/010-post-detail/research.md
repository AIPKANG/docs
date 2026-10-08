# Research: 글 상세 (010-post-detail)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/40-post-detail.md`, 005·006·008·009 구현

사용자 지시: 질문 없이 기본값. 005가 만든 최소 상세(`PostDetailQuery`, `post/detail.html`)를 넓힌다. 아직 없는 기능(댓글 014, 좋아요 015, 조회수 기록 016, 팔로우 018, 신고·숨김 022, 휴지통 011)은 **자리와 데이터만** 두고 각 명세가 동작을 붙인다.

## R-1. 주소 처리 순서 (FR-001~FR-005)
- **Decision**: ① 대문자 주소 301(002 `HandlePathCanonicalizer`) → ② 글 번호가 숫자가 아니면 404(경로 변수를 문자열로 받아 직접 해석 — 형 변환 400을 피함) → ③ 글 번호로 휴지통 밖 글을 찾고 `PostAccessPolicy.canRead`(006) — 볼 수 없으면 404 → ④ 주소의 블로그 주인 ≠ 작성자면 작성자 주소로 301 → ⑤ 작성자 본인의 임시글이면 `/write/{id}`로 302 → ⑥ 표시. 정규 주소는 `/@handle/posts/{id}`(쿼리 없음), `<link rel="canonical">`에 사이트 주소(`blog.markdown.site-origin`)를 붙여 넣는다. `?comment=`는 014가 쓴다.
- 탈퇴 유예 작성자: 작성자 행의 `withdrawn_at`을 같은 조회에서 읽어 `PostFacts.authorWithdrawn`으로 넘긴다(006 판정).

## R-2. 조회 (FR-027)
- **Decision**: 글 + 작성자 JOIN 1번(본문 HTML·요약·시각·카운터·작성자 소개·사진, `content_md` 제외) + 태그 1번 + 작성자일 때 작업본·버퍼 확인(004 `PostEditFacts`) — 최대 3번(좋아요·팔로우 여부는 015·018이 1번씩 더해 5번 이내). 댓글 첫 20개는 014.

## R-3. 화면 (FR-006~FR-020)
- **Decision**: `post/detail.html` 재구성: 작성자에게만 배지(임시저장·🔒 비공개·수정 중), h1 제목, 작성자 영역(`fragments/author` + 사진), 날짜(공개 글은 `first_public_at`, 작성자가 보는 비공개 글은 `published_at`, 009 `CardDates` 형식 + `<time datetime>`), "수정됨 · M월 d일"(`edited_at`만), 작성자 버튼 [수정]·[공개 범위 ▾](006 PATCH, `post-detail.js`)·[삭제] 자리(011), 본문(`th:utext`, 정화 HTML), 태그 `/tags/{인코딩}`, 반응 줄(♥ 수, [좋아요]·[신고] 자리는 인증된 비작성자에게만, "조회 1,234"/"1.2만"), 작성자 카드(사진·닉네임 @주소·소개·[블로그 가기]·[팔로우] — 비회원은 `/login?redirect=`로 가는 링크, 회원 버튼 동작은 018, 내 글이면 없음), 수정 중 안내 "수정 중인 내용이 있어요(M월 d일 HH:mm 저장) [이어서 수정] [변경 취소]", GIF 재생 스크립트(008).
- 조회수 숫자 형식: 1만 미만 `1,234`, 이상 `1.2만`(소수 첫째 자리, 내림).

## R-4. 링크 미리보기·캐시 (FR-024~FR-026)
- **Decision**: 공개 글: `<title>{제목} - {닉네임}</title>`, description·og:description(요약 앞 160자), canonical, og:type article, og:title, og:image(정화된 본문의 첫 `<img src>` — 우리 저장소 주소만 남아 있음. 없으면 `/images/og-default.png`), article:published_time(`first_public_at`), article:modified_time(`edited_at` 있을 때). 값은 Thymeleaf 속성 이스케이프. 작성자 본인이 보는 비공개 글은 `noindex`. 캐시: 공개 글 `Cache-Control: private, no-cache`, 그 밖 `private, no-store`.

## R-5. 조회 기록 (FR-021~FR-023)
- **Decision**: 016(조회수)이 `POST /api/posts/{id}/views`와 `post-view.js`를 만든다. 010은 공개·발행 글을 독자(작성자·관리자 아님)가 볼 때만 본문 요소에 `data-view-post-id`를 넣어 둔다. 상세 응답 경로에서는 조회수를 바꾸지 않는다.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 코드 강조(highlight.js) | 007 U-1과 같이 보류 |
| U-2 | 좋아요·신고·팔로우·삭제·댓글 동작 | 015·022·018·011·014 |

## 구현 메모 (/speckit-implement, 2026-10-08)
- **I-1.** og:image는 `content_html`의 첫 `<img src>`다. GIF 글이면 정지 장면(썸네일)이 잡힌다(23 §5-2의 "OG는 원본 GIF"와 다름 — 메신저 미리보기가 정지 이미지를 더 잘 다룬다는 점에서 그대로 둠, 팀 확인 U-3).
- **I-2.** 005의 "다른 블로그 주소면 404"를 FR-002 ④대로 "볼 수 있으면 301"로 바꿨다. 볼 수 없으면 여전히 404(존재 비노출).
- **I-3.** "수정 중" 안내는 본문을 읽지 않는 버전 조회(`PostEditStore.editVersions`) + 버퍼로 판단한다(FR-027).
- **I-4.** 좋아요·신고·팔로우·삭제 버튼과 댓글 영역, 조회 기록용 `data-view-post-id`는 자리만 두었다(015·022·018·011·014·016).
- **I-5.** 전체 Gradle 테스트 513개 통과.
