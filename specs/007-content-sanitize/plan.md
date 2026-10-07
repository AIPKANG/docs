# Implementation Plan: 본문 렌더링·정화

**Branch**: `007-content-sanitize` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

## Summary

C-POST-1(Tier A). 본문 Markdown을 서버의 단일 `ContentRenderer`가 CommonMark + GFM으로 해석하고, AST 변환(제목 단계 낮추기·외부 이미지 → 링크·중첩 20단계 검사) → 이스케이프·주소 정화 렌더링 → OWASP 허용 목록 정화의 이중 방어로 HTML을 만든다(12 §2). 요약 200자와 우리 저장소 이미지 목록을 함께 돌려줘 005 발행이 쓴다. 로그인 회원용 미리보기 API, 렌더링 버전 기반 다시 렌더링 배치, 글 제목 정리 규칙을 더한다. 005가 이 렌더러에 의존해 007을 먼저 구현한다.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1 · **Primary Dependencies**: + commonmark-java 0.30.0(tables, strikethrough, task-list-items, autolink), OWASP Java HTML Sanitizer 20260924.2 (헌법 기술 제약 표) · **Storage**: V1 `post.content_html`·`excerpt`·`render_version`, 새 마이그레이션 없음 · **Testing**: JUnit 5 단위(52 사례 + 검사기) + Testcontainers 통합 · **Constraints**: 렌더링 1초·중첩 20, 직접 쓴 HTML은 글자로, 우리 저장소 이미지만 · **Scale**: 본문 10만 자 수십 ms

미정 항목 없음. 기본값은 [research.md](./research.md), 확인 권장 U-1~U-3.

## Constitution Check

| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | `post.markdown` 패키지, 다른 모듈 테이블 접근 없음 | PASS | PASS |
| II | 스키마 변경 없음, 한도·주기는 `blog.markdown.*` | PASS | PASS |
| III | 미리보기는 로그인 필요(서버 검사), 다시 렌더링은 내부 작업 | PASS | PASS |
| IV | 이 기능 자체가 원칙 IV의 구현: Markdown 원문 저장, CommonMark+GFM, 허용 목록 정화, 링크·이미지 규칙, 제목 정리, CSP 유지 | PASS | PASS |
| V | 다시 렌더링 실패는 글을 바꾸지 않고 다음 실행 재시도 | PASS | PASS |
| VI | 52 사례 + 통합 테스트(실제 PostgreSQL·Redis) | PASS | PASS |

**위반 없음.**

## Project Structure

```text
src/main/java/com/team/blog/post/
├── markdown/   ContentRenderer, RenderedContent, MarkdownProperties, MarkdownTransformer(AST 변환), HeadingAnchors,
│               LinkAttributeProvider, SanitizePolicy, ExcerptExtractor, NestingDepthChecker, ContentTooComplexException(shared/error)
├── domain/     PostTitleRules
├── application/ MarkdownPreviewService, RerenderService, RerenderJob
└── web/        MarkdownPreviewApiController
src/main/resources/static/js/editor/preview.js, templates/post/editor.html([미리보기])
src/test/java/com/team/blog/post/markdown/  ContentRendererXssTest, ContentRendererSyntaxTest, ContentRendererLimitsTest,
                                            DangerousHtmlChecker(+Test), PostTitleRulesTest
src/test/java/com/team/blog/post/integration/ MarkdownPreviewIT, RerenderIT, SecurityHeadersPostIT
```

**Structure Decision**: 004의 `post` 모듈 안. 다음 단계 `/speckit-tasks`.
