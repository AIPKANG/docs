# Plan: 그룹 공개 — 강성찬 개인 확장
- `V5__friend_groups.sql`: LINK와 함께 `GROUP` CHECK, 세 테이블(03 ERD 이름 그대로).
- friend 모듈에 `GroupService`(그룹·멤버·글 대상·끊기 정리·탈퇴 정리), `GroupVisibilityRule`(`@ConditionalOnProperty`), `GroupPageController`(설정 화면·폼), `GroupDetailSection`(글쓴이 대상 고르기).
- `PostAccessPolicy.friendAndGroupBlogCondition` + `PostListQuery.blogForFriend`(보는 사람 번호를 바인딩 값으로) — 블로그 화면·더 보기 API 같게.
- `FriendService.remove`가 서로의 그룹에서 빼고, `FriendPurgeStep`이 그룹도 정리.
