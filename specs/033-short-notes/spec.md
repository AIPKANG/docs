# Feature Specification: 짧은 기록

**Feature Branch**: `033-short-notes` | **Created**: 2026-10-09 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (01 §2-4 "짧은 기록", 03 ERD `short_note`).

## User Scenarios & Testing
### User Story 1 - 가볍게 남기기 (P1)
블로그 글로 쓰기엔 짧은 생각을 내 블로그 첫 화면의 "짧은 기록" 칸에서 280자까지 남긴다. 공개 범위는 전체 공개·친구 공개·나만 보기. 블로그 첫 화면에 최근 3개, [모두 보기]에서 30개씩 이어 본다. 지우기는 본인만.

## Requirements
- **FR-001** 내용은 글자로만 보인다(HTML·Markdown 해석 없음, 줄바꿈 유지). 1~280자.
- **FR-002** 친구 공개 기록은 친구(025)와 본인만, 나만 보기는 본인만.
- **FR-003** 쓰기는 이메일 인증한 회원만, 한 시간 20개까지(설정값).
- **FR-004** 탈퇴하면 지운다. 홈·검색·트렌딩·sitemap에는 나오지 않는다(블로그 안 기록).
- **FR-005** `blog.notes.enabled=false`면 칸과 화면이 없다.

## Assumptions
- 좋아요·댓글·알림은 달지 않는다(가벼운 기록). 잔디(031)에도 세지 않는다.
