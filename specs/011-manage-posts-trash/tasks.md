---
description: "011-manage-posts-trash 구현 작업 목록"
---
# Tasks: 내 글 관리·휴지통
**작업 ID**: 011은 **`T1101`부터**.
- [X] T1101 설정 `blog.post.trash.*`, `blog.post.manage.page-size`
- [X] T1102 `PostManageQuery` 재작성(탭·필터·커서·개수·SQL 1번), `ManageRow`·`ManagePage`·`ManageTab`, API `GET /api/me/posts`
- [X] T1103 `manage.html` 세 탭·줄 표시·폼, `manage.js`(줄 갱신·확인창·더 보기·실패 이유)
- [X] T1104 `PostTrashService`(삭제·빈 임시글 즉시·버퍼 반영·복구·영구 삭제·사진 해제), `PostTrashApiController`, SSR 폼 경로
- [X] T1105 `TrashPurgeJob`(30일·100개·잠금)
- [X] T1106 [P] `ManagePostsIT`, `PostTrashIT`, `TrashPurgeIT`, 004 테스트의 `/manage/posts` 기대값 갱신
- [X] T1107 전체 테스트·구현 메모
