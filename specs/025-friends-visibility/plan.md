# Implementation Plan: 친구 공개 (FRIENDS) — 강성찬 개인 확장

**Branch**: `025-friends-visibility` | **Date**: 2026-10-09 | **Spec**: [spec.md](./spec.md)

## Summary

06 §6 규격 그대로. 공개 범위 규칙 Bean(`VisibilityRule`)을 **하나 더하는** 방식이라 공통 코드(목록 조건·상세 판정)는 고치지 않는다.
새 모듈 `friend`(요청·수락·거절·취소·끊기·목록, 규칙 Bean, 탈퇴 정리 단계)와 마이그레이션 `V2__friends.sql`(CHECK 교체만)을 더한다.

## Technical Context

Java 21, Spring Boot 4.1, JdbcTemplate, Thymeleaf SSR + 작은 JS, PostgreSQL 18(`friendship`은 V1에 이미 있음), Redis(요청 수 제한), Testcontainers.

## Constitution Check

- 공통 완료 기준 유지(01 원칙 2): 공통 목록 조건 `publicListingCondition`은 `PUBLIC`만 그대로 → 홈·태그·검색·트렌딩·피드·sitemap에 친구 공개 글이 들어갈 길이 없다.
- 서버에서 판정(헌법 III): 상세·댓글·좋아요·조회는 이미 `PostAccessPolicy.canRead`를 거치므로 규칙 Bean만으로 막힌다.
- 비밀값 없음, 새 외부 의존성 없음.

## Project Structure

```
src/main/java/com/team/blog/friend/
  application/  FriendService(요청·수락·거절·취소·끊기), FriendQuery(친구인지·목록), FriendProperties(하루 요청 상한)
                FriendsVisibilityRule(VisibilityRule: 친구만 읽기, 공용 목록 조건 없음), FriendPurgeStep(탈퇴 정리 order 55)
  web/          FriendPageController(POST /@{handle}/friend 폼: request·accept·remove, /settings/friends)

src/main/resources/db/migration/V2__friends.sql
templates/settings/friends.html, fragments/friend.html
```

변경(추가만): `PostAccessPolicy.friendBlogCondition`(친구가 보는 블로그 목록), `PostListQuery.blog(…, includeFriends)`, `NotificationType.FRIEND_REQUEST`,
발행 설정·글 상세·내 글 관리·설정의 공개 범위 선택지에 "친구 공개".

## 단계

1. V2 마이그레이션(post·member 공개 범위 CHECK, 알림 종류 CHECK, 친구 블로그 목록 인덱스)
2. friend 모듈(서비스·조회·규칙·정리 단계·요청 제한) + 테스트
3. 블로그 목록(친구면 친구 공개 글 포함) + API 같은 규칙
4. 화면: 블로그 머리 버튼, 설정 친구 화면, 공개 범위 선택지, 알림
5. 권한 표 테스트(친구 공개 칸), 전체 테스트
