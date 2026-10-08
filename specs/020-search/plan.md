# Implementation Plan: 검색

**Branch**: `020-search` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
글·사람 검색과 블로그 안 검색. 검색어는 NFC·50자·최대 5단어·1글자 무시, 2글자는 제목·태그만, 3글자 이상은 본문까지, 모든 단어 AND, `ILIKE … ESCAPE`로 `% _ \` 그대로. 관련도순은 제목 → 제목·태그 → 본문 단계(단계 안 최신순), 최신순 선택 가능, 9개씩 단계 커서. 단계마다 최근 글 창에서 먼저 찾고 모자라면 가장 긴 단어로 제목·태그·본문 후보를 트라이그램 인덱스로 따로 모은다. 미리보기는 "전체 이스케이프 → 검색어에 `<mark>`". `#태그`는 태그 목록으로. 같은 방문자 1분 30번, 색인 금지.

## Constitution Check
I(discovery가 post 공용 조건·account 블로그 주인만 씀) · II(V1의 `pg_trgm` 인덱스 그대로, 기준값 `blog.search.*`) · III(006 공용 조건 + 숨김 아님) · IV(미리보기 이스케이프 후 강조, 스크립트는 조각을 글자로) · V(제한 저장소 장애에 열어 둠) · VI(Testcontainers로 단계·커서·인덱스 단계 검증) — 위반 없음.

## Project Structure
```text
discovery/application/{SearchTerms, SearchSnippet, SearchProperties, SearchService, VisitorKeys(016에서 분리)}
discovery/web/{SearchController, BlogPageController(?q=)}, templates/search/results.html, layout/base.html(검색창)
static/js/post/list-more.js(검색 미리보기 조각)
tests: discovery/integration/SearchIT, discovery/unit/SearchTermsTest
```
