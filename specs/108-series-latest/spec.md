# Feature Specification: 시리즈 최근 업데이트

**Feature Branch**: `108-series-latest` | **Created**: 2026-10-16 | **Status**: Implemented (blog 1.65.0)

**Scope**: **강성찬 개인 확장**.

## 결정
- 시리즈 카드(블로그의 시리즈 목록, 탐색 [시리즈] 탭)에 "글 N편 · 날짜 업데이트". 날짜는 시리즈 글이 마지막으로 공개된 날.
- 블로그의 시리즈 목록은 최근 업데이트 순.
- 탐색 [시리즈] 탭은 60개씩 보이고, 넘으면 [더 보기]로 다음 60개, 끝에서 [처음부터 보기].
- 팔로워 새 글 알림: 시리즈에 든 글이면 "○○님의 시리즈 「이름」 새 편: 「제목」"(알림을 읽을 때 정한다).
- DB 바뀜 없음.

## Success Criteria
- 통합 테스트: 시리즈 카드의 "글 N편 · … 업데이트"(ProfileTabsIT), 탐색 시리즈 더 보기(ExploreIT), 새 편 알림 문구(NotificationIT).
