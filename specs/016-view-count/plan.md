# Implementation Plan: 조회수

**Branch**: `016-view-count` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-VIEW-1(Tier B). 글이 1초 보인 뒤 브라우저가 한 번 `POST /api/posts/{id}/views`(항상 204). 방문자(회원 → 쿠키 `vid` → IP·UA·날마다 바뀌는 비밀값 해시) × 글마다 기간 안에 설정 횟수까지만 Redis Lua로 원자 집계하고, 1분마다 글별 1번 UPDATE·UPSERT로 `post.view_count`·`post_view_daily`에 반영한다. 작성자·관리자·봇·미리 불러오기·볼 수 없는 글은 세지 않는다. Redis 실패는 건너뛴다.

## Constitution Check
I(discovery → post 공개 Service `PostReadAccess`) · II(스키마 그대로, 기준값 `blog.view.*`) · III(006 판정, 404 동일) · V(Redis 실패에도 상세·응답 정상, 반영 실패 복구) · VI(Testcontainers 동시성·반영 통합 테스트) — 위반 없음.

## Project Structure
```text
discovery/application/{ViewProperties, ViewRecorder, ViewFlusher, ViewJobs}
discovery/web/{ViewApiController, ViewDetailSection}, shared/web/VisitorCookie
post/web/PostDetailController(쿠키 발급·recordView), templates/post/detail.html, static/js/post/post-view.js
tests: discovery/integration/ViewCountIT, discovery/unit/ViewRecorderTest
```
