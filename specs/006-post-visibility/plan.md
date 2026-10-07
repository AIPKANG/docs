# Implementation Plan: 공개 범위

**Branch**: `006-post-visibility` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

## Summary
C-POST-4(Tier A). 005의 `PostAccessPolicy`를 공개 범위 규칙 Bean(`PUBLIC`, `PRIVATE`) 위에 다시 세우고, 탈퇴 유예 작성자 제외·임시글 규칙·공용 목록 조건을 한곳에 둔다. 다시 발행 없이 공개 범위를 바꾸는 API(행 잠금, `first_public_at` 한 번, `edited_at`·작업본 불변, 사건), 볼 수 없는 글의 공통 404(OG·noindex·no-store), 내 글 목록 배지·버튼을 더한다. 친구 공개는 선택 규격이라 이번에는 구현하지 않는다(규칙 추가로 확장 가능).

## Technical Context
Java 21 · Boot 4.1.1 · 새 의존성·마이그레이션 없음 · Testcontainers.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | post 모듈 안, account는 `MemberSummaryQuery` 등 공개 Service | PASS | PASS |
| II | 스키마 변경 없음, 공개 범위는 규칙 Bean 추가로 확장 | PASS | PASS |
| III | 판정 한 곳·목록 조건 한 곳, 없는 글과 같은 404, 관리자 예외 없음 | PASS | PASS |
| IV | 화면 출력 이스케이프, CSP 그대로 | PASS | PASS |
| V | 사건은 커밋 후 구독 | PASS | PASS |
| VI | 접근 표 통합 테스트 | PASS | PASS |

## Project Structure
```text
post/application/visibility/{VisibilityRule, PublicVisibilityRule, PrivateVisibilityRule, PostFacts}
post/application/{PostAccessPolicy(재작성), PostVisibilityService}, post/web/PostVisibilityApiController
shared/event/PostVisibilityChanged, templates/error/404.html·layout(OG·noindex), templates/post/manage.html, static/js/post/manage.js
tests: post/integration/{PostAccessMatrixIT, VisibilityChangeIT, PublicListingConditionIT}
```
