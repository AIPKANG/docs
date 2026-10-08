# Feature Specification: 잔디·스트릭

**Feature Branch**: `031-activity-streak` | **Created**: 2026-10-09 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (01 §2-4 "잔디·스트릭", 03 ERD `daily_activity` 자리).

## User Scenarios & Testing
### User Story 1 - 블로그에서 꾸준함 보기 (P1)
개인 블로그 첫 화면에 최근 1년(53주 × 7일)의 기록 칸이 보인다. 그날 공개 글을 올렸거나 공개 글에 댓글을 쓴 만큼 칸이 진해진다(5단계). 칸에 마우스를 올리면 "2026.10.09 · 글 1 · 댓글 2". 위에 "🔥 N일 연속"(오늘 또는 어제까지 이어진 날 수), "최근 1년 N일 · 최고 N일 연속".

## Requirements
- **FR-001** 누구나 보는 화면이므로 공개 글(최초 공개일)과 공개 글에 단 댓글만 센다. 비공개·친구·그룹·링크 글과 그 댓글은 세지 않는다.
- **FR-002** 날짜는 한국 시간(설정 `blog.activity.zone`).
- **FR-003** 글 1개 = 댓글 2개 무게로 진하기 0~4.
- **FR-004** `blog.activity.enabled=false`면 보이지 않는다. 블로그 이어 보기·태그 필터 화면에는 그리지 않는다.

## Success Criteria
- **SC-001** 비공개 활동이 칸·연속 일수에 드러나는 경우 0건.

## Assumptions
- 03 ERD의 `daily_activity` 테이블은 만들지 않았다. 작성자 한 명의 1년치를 화면을 열 때 세도 가볍기 때문(작성자 색인 사용). 사람이 많아지면 그때 날짜별 집계 테이블로 바꾼다.
