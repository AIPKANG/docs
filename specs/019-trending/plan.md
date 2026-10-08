# Implementation Plan: 트렌딩

**Branch**: `019-trending` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
홈 `[최신] [트렌딩]` 탭. 10분마다 공용 목록 조건(+숨김 아님) 안의 최근 7일 글을 `(3×좋아요 + 2×작성자 외 댓글 작성자 + 0.1×조회) ÷ (경과 시간+2)^1.5`로 점수 매겨 작성자당 3개·상위 100개를 Redis 스냅샷(30분)으로 둔다. 커서 `{스냅샷}:{위치}`로 보던 순위를 그대로 넘기고, 그 사이 볼 수 없게 된 글은 건너뛴다. 사라진 스냅샷 410, Redis 장애 때 DB 직접 계산 첫 9개.

## Constitution Check
I(discovery가 post 공개 Service `PostListQuery.cardsByIds`·`PostAccessPolicy`만 씀) · II(스키마 그대로, 가중치·기간·개수·주기 `blog.trending.*`) · III(목록 조건은 006 공용 조건) · V(Redis 장애에도 탭이 비지 않음) · VI(Testcontainers로 점수·스냅샷 넘기기 검증) — 위반 없음.

## Project Structure
```text
discovery/application/{TrendingProperties, TrendingService, TrendingJob}, discovery/web/HomeController(+tab, /api/posts/trending)
post/application/PostListQuery(+cardsByIds), shared/error/SnapshotExpiredException(410)
templates/home.html(탭·안내·빈 상태), fragments/post-card.html(+moreWith), static/js/post/list-more.js(링크 모양·410)
tests: discovery/integration/TrendingIT, discovery/unit/TrendingFallbackTest
```
