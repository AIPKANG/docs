# Feature Specification: 친구·그룹 한 화면, 비공개 계정 스위치는 계정에

**Feature Branch**: `087-friends-groups-one-screen` | **Created**: 2026-10-14 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-14 겹치는 화면 점검 6번, chan "추천해주는대로 다 만들어줘"). 화면 배치만 바꾸고 기능·저장 방식은 그대로. 친구(069)·그룹(030)·비공개 계정(073)의 기존 끄기 스위치를 그대로 따른다.

## 확인한 겹침
- `/settings/groups`는 `/settings/friends`와 같은 "내 친구" 목록(`FriendQuery.friends`)을 다시 읽어 그룹에 넣을 사람을 고르는 화면이었고, 두 화면이 서로를 링크로 오갔다. 합쳐도 빠지는 기능이 없다(그룹 만들기·넣기·빼기·지우기, 그룹 공개 글의 그룹 고르기 그대로).
- 비공개 계정 켜기·끄기는 계정 성격이라 설정 > 계정으로 옮기고, 받은 팔로우 요청 목록은 알림이 가리키는 `/settings/privacy`에 둔다.

## Requirements
- **FR-001** `/settings/friends`(제목 "친구·그룹")에 내 친구 · 내가 팔로우하는 사람 · 친구 그룹(`#groups`) 순서로 둔다.
- **FR-002** `GET /settings/groups`는 `/settings/friends#groups`로 보낸다(303). 그룹 폼(만들기·지우기·넣기·빼기)도 처리 뒤 그 자리로.
- **FR-003** 설정 > 계정에 비공개 계정 스위치(`role="switch"`, `aria-checked`)를 둔다. 누르면 `POST /settings/privacy`(back=`/settings#settings-account`)로 바꾸고 그 자리로 돌아온다. back은 같은 사이트 경로만, 아니면 `/settings/privacy`.
- **FR-004** `/settings/privacy`는 지금 상태 한 줄과 받은 팔로우 요청(수락·거절)만 보여 주고, 켜고 끄기는 설정 > 계정으로 안내한다.

## Success Criteria
- GroupIT: 예전 주소가 친구 화면 #groups로, 친구 화면에 그룹 칸, 그룹 폼이 그 자리로 돌아옴, 로그인 필요, 이름 검사 그대로.
- PrivateAccountIT: 설정 화면 스위치 상태(켜짐/꺼짐), back으로 돌아옴, 다른 사이트 back은 무시.
