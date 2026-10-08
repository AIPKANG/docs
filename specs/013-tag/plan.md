# Implementation Plan: 태그

**Branch**: `013-tag` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-TAG-1(Tier B). 005의 정규화·저장 위에 태그별 글 목록(009 카드·커서)·전체 태그 상위 100(10분 캐시)·자동완성(내 태그 + 공개 태그)·블로그 태그 줄과 필터·주소 인코딩/301/404·발행 오류의 입력값을 더한다.

## Constitution Check
| 원칙 | 확인 | 결과 |
|---|---|---|
| I | 공개 글 판단은 post 모듈(006 조건), 태그 쓰기는 tag 모듈 | PASS |
| II | 스키마 그대로, 개수·캐시 설정값 | PASS |
| III | 공개 글만 세고 보임, 자동완성은 남의 비공개 태그 제외 | PASS |
| IV | 태그 글자 이스케이프, 주소 인코딩 | PASS |
| VI | 통합 테스트 | PASS |

## Project Structure
```text
post/application/TagListingQuery, PostListQuery(+tag), discovery/web/{TagPageController, BlogPageController(+tag), PostListApiController(+tag)}
templates/tag/{tag,tags}.html, blog/home.html(태그 줄), static/js/editor/publish.js(자동완성·순서)
tests: tag/integration/TagPagesIT
```
