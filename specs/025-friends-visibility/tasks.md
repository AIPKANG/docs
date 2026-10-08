# Tasks: 친구 공개 — 강성찬 개인 확장

- [x] T001 V2__friends.sql: post·member 공개 범위 CHECK에 FRIENDS, 알림 종류·묶음 CHECK에 FRIEND_REQUEST, 친구 블로그 목록 인덱스
- [x] T002 friend 모듈: FriendProperties, FriendQuery(areFriends·status·목록), FriendService(요청·수락·거절·취소·끊기, 맞요청 합치기, 하루 상한)
- [x] T003 FriendsVisibilityRule(친구만 읽기, 공용 목록 없음)
- [x] T004 FriendPurgeStep(탈퇴 정리 order 55)
- [x] T005 NotificationType.FRIEND_REQUEST(묶음, 끌 수 없음), 메시지·링크, 사건 FriendRequested/FriendRequestClosed
- [x] T006 PostAccessPolicy.friendBlogCondition + PostListQuery 블로그 목록에 친구 포함(페이지·API·태그 필터 같게)
- [x] T007 FriendPageController(POST /@주소/friend 폼, /settings/friends) + 블로그 머리 버튼(fragments/friend.html, 스크립트 없이 폼으로 동작)
- [x] T008 공개 범위 선택지에 "친구 공개": 발행 설정, 글 상세, 내 글 관리, 설정 기본 공개 범위, 상태 배지
- [x] T009 FriendIT: 요청·수락·거절(알림 없음)·맞요청 20번·끊기 즉시 404·목록 노출 없음(홈·태그·검색·트렌딩·피드)·친구 블로그 목록·탈퇴 정리·하루 상한·권한 표 칸
- [x] T010 기존 테스트 중 FRIENDS 400 기대 수정, 전체 테스트, CHANGELOG
