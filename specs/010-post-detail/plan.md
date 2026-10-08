# Implementation Plan: 글 상세

**Branch**: `010-post-detail` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-READ-2(Tier A). 005의 최소 상세를 명세 순서(301 → 숫자 → 볼 수 있음 → 블로그 일치 301 → 임시글 302 → 표시)로 다시 짜고, 화면 구성(작성자 배지·버튼·날짜 규칙·태그 링크·반응 줄·작성자 카드·수정 중 안내), 링크 미리보기·canonical·캐시 지시, 조회 3번 이내를 맞춘다. 댓글·좋아요·조회 기록·팔로우·신고·삭제는 자리만 두고 각 명세가 붙인다.

## Technical Context
Java 21 · Boot 4.1.1 · 새 의존성·마이그레이션 없음.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | post 조회 Service, 화면은 post.web | PASS | PASS |
| II | 스키마 그대로 | PASS | PASS |
| III | 006 단일 판정, 404 통일, 화면 버튼은 작성자에게만(서버 검사는 각 기능) | PASS | PASS |
| IV | 본문은 정화 HTML만 utext, 메타 값 속성 이스케이프, 인라인 스크립트 없음 | PASS | PASS |
| V | 조회 기록은 응답 경로 밖(016) | PASS | PASS |
| VI | 통합 테스트(주소 처리·접근·메타·캐시·조회 수) | PASS | PASS |

## Project Structure
```text
post/application/{PostDetailQuery(재작성), PostDetail, ViewCountFormat}
post/web/PostDetailController(재작성), templates/post/detail.html, static/js/post/post-detail.js, static/images/og-default.png
tests: post/integration/PostDetailPageIT, post/unit/ViewCountFormatTest
```
