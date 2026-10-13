# Feature Specification: 테스트용 긴 글

**Feature Branch**: `083-sample-posts` | **Created**: 2026-10-13 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-13 chan: 라이브 블로그에 테스트용 글을 길게 더 넣어 달라).

## 결정
- 라이브 서버에는 직접 로그인할 수 없으므로 글은 코드와 함께 배포되고, 관리자가 대시보드(`/admin`)에서 버튼 한 번으로 넣는다. 시작할 때 자동으로 넣지 않는다(지운 뒤 다시 생기지 않게).
- 쓰는 사람은 표시가 분명한 테스트 계정 4개(`test_minji`·`test_junho`·`test_seoyeon`·`test_doyun`, 별명 "테스트…", 소개 "테스트용 계정이에요"). 남의 글이어야 피드·탐색·추천 글·관심 있을 만한 글에 보이기 때문. 로그인은 못 한다(아무도 모르는 비밀번호, 이메일 `@sample.invalid`).
- 글 12편은 `backend/src/main/resources/sample-posts/NN.md`(머리 `author:`·`title:`·`tags:` + `---`). 글마다 h2/h3 여러 개, 목록, 코드, 표, 인용이 있어 목차·스크롤을 시험할 수 있고, 미션 주제 글 4편 포함. 발행 시각은 5시간씩 앞으로 나눈다.
- 다시 눌러도 같은 계정·같은 제목 글은 건너뛴다.
- [모두 지우기]는 탈퇴 정리 단계(023)를 그대로 돌려 테스트 계정의 글·댓글·좋아요·팔로우·알림을 함께 지우고, 아이디는 `gone_번호`로 비워 다시 넣을 수 있게 한다.
- 마이그레이션 없음. 기존 데이터는 건드리지 않는다. 넣기·지우기는 관리 기록에 남는다.
- 끄기: `blog.admin.sample-posts-enabled=false`(서버 환경 변수 `BLOG_ADMIN_SAMPLE_POSTS_ENABLED=false`)면 버튼이 없고 주소는 404.

## Requirements
- **FR-001** `POST /admin/sample-posts` 관리자만, 계정·글이 없으면 만들고 발행(PUBLIC).
- **FR-002** `POST /admin/sample-posts/remove` 관리자만, 테스트 계정과 그 데이터를 모두 정리.
- **FR-003** 관리자가 아니면 404.

## Success Criteria
- SamplePostsIT: 넣기 → 12편 이상 공개, 상세에 h2·표·인용·코드, 다시 넣어도 그대로, 지우면 0, 다시 넣기 가능. 전체 테스트 통과.
