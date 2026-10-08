# Feature Specification: 홈 "새로 온 작가"

**Feature Branch**: `036-home-newcomers` | **Created**: 2026-10-09 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (01 비교표 "홈: 트렌딩 + 첫 글·새 작가").

## User Scenarios & Testing
### User Story 1 - 새 작가 반기기 (P1)
홈 최신 탭 첫 화면 카드 위에 "새로 온 작가 · 첫 글을 반겨 주세요"가 보인다. 최근 14일 안에 처음으로 공개 글을 올린 사람 6명까지, 프로필과 "첫 글 · 제목" 링크.

## Requirements
- **FR-001** 첫 글 = 그 사람의 지금 공개인 글 중 가장 먼저 공개된 글(공용 목록 조건). 그 시각이 기간 안일 때만 새 작가.
- **FR-002** 트렌딩 탭·이어 보기 화면에는 없다. 비어 있으면 영역도 없다.
- **FR-003** 기간·인원·켜기는 설정(`blog.home.newcomers.*`).

## Assumptions
- 트렌딩(019)은 공통 그대로, 이 영역은 최신 탭에 더한다.
