# Implementation Plan: 전체 글 목록·개인 블로그

**Branch**: `009-post-list-blog` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-READ-1·C-BLOG-1(Tier A). 006 공용 목록 조건 하나로 홈·개인 블로그 목록을 만든다: `(first_public_at, id)` 커서, 목록당 SQL 1번(카드 칸만), 9개 고정·10개 조회로 끝 판단, SSR 첫 페이지와 `?cursor` 링크, JS [더 보기]·실패 재시도·뒤로 가기 30분 복원, 반응형 1·2·3열 카드, 블로그 머리말의 공개 글 수, 빈 상태 문구.

## Technical Context
Java 21 · Boot 4.1.1 · 새 의존성·마이그레이션 없음 · Testcontainers.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | 조회는 post 모듈 Service, 화면은 discovery | PASS | PASS |
| II | 스키마 그대로, 크기·복원 시간 설정값 | PASS | PASS |
| III | 006 공용 조건만 사용, 본인 블로그도 비공개 제외 | PASS | PASS |
| IV | 제목·요약·닉네임 글자로만, 썸네일은 우리 저장소 주소 | PASS | PASS |
| V | 해당 없음(읽기) | PASS | PASS |
| VI | 통합 테스트(커서·조건·쿼리 수) | PASS | PASS |
비기능: 목록 쿼리 수 일정(N+1 금지), 본문 컬럼 미조회, 375px 가로 스크롤 없음.

## Project Structure
```text
post/application/{PostListQuery, PostCard, CardPage, FeedCursor, CardDates}
discovery/web/{HomeController, BlogPageController, PostListApiController}
templates/{home.html, blog/home.html, fragments/post-card.html}, static/js/post/list-more.js
tests: post/integration/{PostListIT, BlogPageListIT}, post/unit/{FeedCursorTest, CardDatesTest}
```
