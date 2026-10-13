# Feature Specification: 기능 다듬기(알림 모두 읽음, 피드 나누기, 태그 추천 20자, 발행 팝업)

**Feature Branch**: `068-feature-polish` | **Created**: 2026-10-13 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-13 chan 요청 목록 2·7·13·15번). DB 바뀜 없음.

## User Scenarios & Testing
### User Story 1 - 알림 창을 열면 모두 읽음 (P1)
머리말 종을 눌러 알림 창을 열면 안 읽은 알림이 모두 읽음이 되고 종 숫자가 사라진다. 이번에 연 창에서는 안 읽음 점을 그대로 둬서 무엇이 새것이었는지 보인다(다음에 열면 점이 없다).

### User Story 2 - 피드를 추천 블로그와 팔로잉 블로그 글로 나눔 (P1)
/feed 첫 화면 위에 "추천 블로그"(아직 팔로우하지 않은 작가, 최대 6명, 팔로우 버튼), 아래에 "팔로잉 블로그 글"(지금 피드 그대로)이 있다.

### User Story 3 - AI 태그 추천은 20자부터 (P2)
제목+본문(정리 뒤) 20자 이상이면 AI 태그 추천을 받을 수 있다(전에는 100자).

### User Story 4 - [발행]을 누르면 발행 설정 팝업 (P1)
글쓰기에서 [발행]을 누르면 아래로 펼치던 발행 설정이 화면 가운데 모달 창으로 뜬다. Esc·바깥 누름·[취소]로 닫는다.
스크롤보다 팝업을 고른 까닭: 긴 글에서도 본문 위치를 잃지 않고, 설정을 마치거나 취소하는 흐름이 한 곳에 모인다.

## Requirements
- **FR-001** bell.js: 펼친 목록에 안 읽은 알림이 있으면 `POST /api/notifications/read-all`을 부르고 성공하면 숫자를 0으로. 목록 표시는 그대로.
- **FR-002** RecommendedBlogQuery: 공용 목록 조건을 거친 글이 있는 작가 중 나와 내가 팔로우한 사람을 빼고, 최근 30일에 공개 글을 쓴 사람 먼저 → 좋아요 합 → 마지막 공개 시각 순 6명. 피드 첫 쪽에서만 보이고 커서로 넘긴 쪽에서는 안 보인다. 버튼은 기존 팔로우 조각과 follow.js.
- **FR-003** `blog.ai.tag-suggest.min-chars` 기본값 100 → 20(application.yml, AiTagProperties).
- **FR-004** 발행 설정 `#publish-panel`을 `<dialog>`로 바꾸고 `showModal()`로 연다. 안의 AI 동의 창은 그대로 위에 뜬다. 충돌(409)이면 창을 닫고 비교 창을 연다.

## Success Criteria
- FollowIT: 추천 블로그에 팔로우 안 한 작가가 있고 나·팔로우한 사람은 없음, 다음 쪽에는 추천 블로그 없음.
- FollowIT·NotificationIT·AiTagSuggestIT·EditorPageIT 통과.
