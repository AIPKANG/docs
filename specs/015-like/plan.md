# Implementation Plan: 좋아요

**Branch**: `015-like` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-LIKE-1(Tier B). 상태 지정형 좋아요(PUT/DELETE, 멱등), 기록·수 원자 갱신과 실제 변화 때만 사건, 1분 60번, 매일 보정, 상세의 내 상태·낙관적 버튼(0.3초 디바운스)·스크립트 없는 폼.

## Constitution Check
I(interaction → post 공개 Service) · II(스키마 그대로) · III(006 판정, 자기 글 400) · V(사건 커밋 후, 멱등) · VI(동시성 통합 테스트) — 위반 없음.

## Project Structure
```text
interaction/application/{LikeService, LikeReconcileJob}, interaction/web/{LikeController, LikeDetailSection}
post/application/PostCounters(+likeCount, reconcileLikes), shared/event/{PostLiked, PostUnliked}
static/js/post/like.js, templates/post/detail.html(반응 줄)
tests: interaction/integration/LikeIT, PermissionMatrixIT 표 4
```
