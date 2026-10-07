# Implementation Plan: 글 발행·수정(다시 발행)

**Branch**: `005-post-publish` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

## Summary

C-POST-3(Tier A). 004의 편집 버전·작업본·버퍼와 007의 렌더러를 이어 [발행]을 만든다: 트랜잭션 밖에서 검증(실패 칸 모두)·렌더링·요약, 트랜잭션 안에서 행 잠금·현재 버전 확인·태그 확정·글 반영(시각 규칙)·작업본 삭제, 커밋 후 버전 조건부 버퍼 정리·사건·멱등 응답 저장. `Idempotency-Key`로 연타·재전송을 한 번으로 묶고 Redis 장애 때는 잠금·버전으로 막는다. 태그 정규화(`tag` 모듈 시작)와 글 상세 최소 화면(접근 정책 한 곳)을 함께 만든다.

## Technical Context
Java 21 · Boot 4.1.1 · 새 의존성 없음 · PostgreSQL(V1 그대로)·Redis(멱등 키) · Testcontainers · 같은 글 동시 발행은 행 잠금으로 직렬화 · 수치는 `blog.post.*`.

미정 없음. [research.md](./research.md) U-1~U-3.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | post → tag는 공개 Service(`PostTagService`), 사건은 shared.event | PASS | PASS |
| II | 스키마 변경 없음, 태그 수·TTL 설정값 | PASS | PASS |
| III | 작성자는 인증 정보로만, 남의 글·휴지통·관리자 404, 상세 판정은 `PostAccessPolicy` 한 곳 | PASS | PASS |
| IV | 007 렌더러·제목 정리, 상세는 정화 HTML만 `utext`, 제목·태그는 글자 | PASS | PASS |
| V | 렌더링은 트랜잭션 밖, 버퍼 정리·사건은 커밋 후, 멱등 키로 결과 한 번 | PASS | PASS |
| VI | 통합 테스트(동시 20건·권한 표·XSS) | PASS | PASS |

**위반 없음.**

## Project Structure
```text
src/main/java/com/team/blog/
├── post/application/ PostPublishService, PublishCommand, PublishResult, PublishValidator, PublishIdempotency,
│                     PostAccessPolicy, PostDetailQuery, PostView, PublishImageLinker(008 자리)
├── post/infra/       PostEditStore(+lockForPublish, applyPublish), RedisAutosaveBuffer(+evictUpTo)
├── post/web/         PostPublishApiController, PostDetailController
├── tag/{domain/TagNormalizer, TagRejectedException, application/PostTagService, infra/TagStore}
└── shared/{event/PostPublished, PostEdited; error/IdempotencyKeyReusedException, PublishInProgressException}
templates/post/{editor.html(+발행 설정), detail.html}, static/js/editor/publish.js
tests: tag/unit/TagNormalizerTest, post/integration/{PublishIT, RepublishIT, PublishIdempotencyIT, PublishPermissionIT,
       PublishConflictIT, PostDetailIT}
```
다음: `/speckit-tasks`.
