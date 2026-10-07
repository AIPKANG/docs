---
description: "007-content-sanitize 구현 작업 목록"
---

# Tasks: 본문 렌더링·정화

**작업 ID**: 007은 **`T401`부터**. 004의 `post` 모듈·`JobLock`·`RedisRateLimiter`·`GlobalExceptionHandler`를 확장한다.

## Phase 1: Setup
- [X] T401 `build.gradle.kts`에 commonmark 0.30.0(+tables, strikethrough, task-list-items, autolink)과 OWASP sanitizer 20260924.2
- [X] T402 [P] `application.yml` `blog.markdown.*`, `application-test.yml` `rerender.enabled: false`, `post/markdown/MarkdownProperties`

## Phase 2: Foundational
- [X] T403 [P] `shared/error/ContentTooComplexException` → 400 `CONTENT_TOO_COMPLEX`, 문구
- [X] T404 [P] 테스트 도구 `post/markdown/DangerousHtmlChecker` + `DangerousHtmlCheckerTest`(위험 6/안전 3, SC-007)

## Phase 3: US1·US2 — 안전하고 의도대로 렌더링 (P1)
- [X] T405 [P] [US1] `ContentRendererXssTest`: 12 §9-1의 32개 공격 문자열 → 검사기 통과(SC-001)
- [X] T406 [P] [US2] `ContentRendererSyntaxTest`: 12 §9-2의 13개 정상 문법, `h1` 없음, 앵커 중복 `-1`, 외부/내부 링크, 외부 이미지 → 링크, 직접 쓴 HTML은 글자(SC-002)
- [X] T407 [US1] `MarkdownTransformer`(제목 +1, 외부 이미지 → 링크), `HeadingAnchors`, `LinkAttributeProvider`, `SanitizePolicy`, `ContentRenderer`, `RenderedContent`, `ExcerptExtractor`(10 §2-1)

## Phase 4: US3 — 미리보기 (P2)
- [X] T408 [P] [US3] `MarkdownPreviewIT`: 결과 = `ContentRenderer` 결과(SC-003), 비회원 401, 인증 전 회원 허용, 1분 60회 초과 429, 10만 자 초과 400
- [X] T409 [US3] `MarkdownPreviewService`, `MarkdownPreviewApiController`, `static/js/editor/preview.js`, 편집 화면 [미리보기]

## Phase 5: US4 — 복잡한 본문 거부 (P2)
- [X] T410 [P] [US4] `ContentRendererLimitsTest`: 인용·목록 25단계 거부, 15단계 허용, 10만 자 1초 안(SC-004)
- [X] T411 [US4] `NestingDepthChecker`, 렌더링 스레드 풀 + 1초 제한

## Phase 6: US5 — 제목 정리 (P2)
- [X] T412 [P] [US5] `PostTitleRulesTest`(U+202E·폭 0·제어 문자 제거, NFD → NFC, 앞뒤 공백)
- [X] T413 [US5] `post/domain/PostTitleRules`

## Phase 7: US6 — 다시 렌더링 (P3)
- [X] T414 [P] [US6] `RerenderIT`: 이전 버전 발행 글만 갱신, `edited_at`·`edit_version`·`updated_at` 그대로, 100개 단위, 임시글 제외(SC-006)
- [X] T415 [US6] `RerenderService`, `RerenderJob`(잠금, 설정)

## Phase 8: Polish
- [X] T416 [P] `SecurityHeadersPostIT`: `/write/{id}`·미리보기 응답에 CSP·nosniff·Referrer-Policy(SC-005)
- [X] T417 quickstart 확인, research 구현 메모, 전체 테스트
