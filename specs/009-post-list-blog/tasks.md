---
description: "009-post-list-blog 구현 작업 목록"
---
# Tasks: 전체 글 목록·개인 블로그
**작업 ID**: 009는 **`T901`부터**.
- [ ] T901 [P] `FeedCursor`(Base64URL, `INVALID_CURSOR`) + 단위 테스트
- [ ] T902 [P] `CardDates`(N분 전·N시간 전·yyyy.MM.dd) + 단위 테스트
- [ ] T903 `PostListQuery`(feed·blog·publicCount, SQL 1번), `PostCard`·`CardPage`
- [ ] T904 [US1] `HomeController`(SSR, `?cursor`), `fragments/post-card.html`, `home.html`, 빈 상태
- [ ] T905 [US2] `BlogPageController` 목록·공개 글 수·빈 상태(본인/남)
- [ ] T906 `PostListApiController` `/api/posts`, `/api/members/{handle}/posts`
- [ ] T907 `list-more.js`(더 보기·재시도·끝·중복 건너뛰기·30분 복원), 카드 CSS(3/2/1열, 16:9, 3줄)
- [ ] T908 [P] `PostListIT`: 조건·정렬·9개·끝 판단·커서 이어 보기 중 새 글/비공개/삭제에도 중복·누락 없음·400·SQL 1번
- [ ] T909 [P] `BlogPageListIT`: 블로그 목록·본인 비공개 제외·공개 글 수·빈 상태·404·SSR 링크
- [ ] T910 전체 테스트·화면 확인·구현 메모
