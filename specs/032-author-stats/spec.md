# Feature Specification: 작성자 통계

**Feature Branch**: `032-author-stats` | **Created**: 2026-10-09 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (강성찬 OPEN-15 작성자 통계, 31 W-7 `post_view_daily`).

## User Scenarios & Testing
### User Story 1 - 내 글이 얼마나 읽혔는지 (P1)
"내 글"의 [통계]에서 발행 글 수·전체 조회·좋아요·댓글·팔로워 합계, 최근 30일 날짜별 조회 막대그래프(숫자 표로도), 30일 동안 많이 읽힌 글 5개를 본다. 본인만 볼 수 있다(비회원은 로그인으로).

## Requirements
- **FR-001** 조회수는 016 기준으로 센 값(`post_view_daily`, 90일 보관)을 쓴다.
- **FR-002** 남의 글 조회는 섞이지 않는다.
- **FR-003** 그래프는 스크립트 없이 그리고, 같은 내용을 표로도 준다(접근성).
- **FR-004** `blog.stats.enabled=false`면 화면이 없다.

## Success Criteria
- **SC-001** 합계·30일 값이 테스트 데이터와 일치.

## Assumptions
- 그래프는 조회만(좋아요·댓글 날짜별 추이는 다음에). 통계 화면 링크는 "내 글" 탭 줄에 둔다.
