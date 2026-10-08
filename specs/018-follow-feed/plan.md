# Implementation Plan: 팔로우·팔로잉 피드

**Branch**: `018-follow-feed` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
회원(=블로그) 단방향 팔로우를 상태 지정형 PUT/DELETE로(멱등·동시 안전, 실제 변화 때만 사건). 팔로워·팔로잉 수와 목록(누구나, 탈퇴 유예 제외, 20개씩 커서), 블로그 상단·글 상세 작성자 카드의 팔로우 버튼(낙관적, 스크립트 없는 폼), 로그인 회원 전용 `/feed`(홈과 같은 카드·정렬·9개 커서, 팔로우한 사람의 공개 글만), 새 팔로워 알림 연결, 익명 처리 정리 단계.

## Constitution Check
I(팔로우는 `interaction`, 피드 화면은 `discovery`, 피드 조건은 post의 `PostListQuery` 공용 조건에 팔로우 조건만 더함) · II(V1 `follow` 그대로, 기준값 설정) · III(로그인 정보로만, 없는·탈퇴 블로그 404, 자기 자신 400 + DB CHECK) · V(사건 커밋 후, IP 제한 저장소 실패는 열어 둠) · VI(동시 20요청·피드 커서 통합 테스트) — 위반 없음.

## Project Structure
```text
interaction/application/{FollowProperties, FollowService, FollowQuery}
interaction/web/{FollowApiController, FollowPageController, FollowDetailSection}
discovery/web/{FeedController, BlogPageController(+수·버튼)}, post/application/PostListQuery(+followingFeed)
notification/application/NotificationListeners(+MemberFollowed/Unfollowed), shared/event/{MemberFollowed, MemberUnfollowed}
shared/error/CannotFollowSelfException, templates/{feed, follow/list, fragments/follow}.html, static/js/follow/follow.js
tests: interaction/integration/FollowIT, PermissionMatrixIT §10-1
```
