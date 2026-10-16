# Feature Specification: 릴리스 노트 나눠 불러오기

**Feature Branch**: `112-releases-scroll` | **Created**: 2026-10-16 | **Status**: Implemented (blog 1.66.0)

**Scope**: **강성찬 개인 확장**(10-16 chan "릴리스 노트 한번에 로드하지말고 조금 내리면 로드하고").

## 바꾸기 전(071, 1.65.4)
- /releases가 CHANGELOG 전체(86개 버전, 사진 240장, HTML 약 130KB)를 한 번에 그렸다.

## 결정
- 서버가 CHANGELOG를 버전마다 `<section id="v1.60.0">`으로 나눠 한 번 그려 둔다. /releases는 최신 10개, `/releases/more?from=N`은 다음 10개 조각.
- 091과 같은 거리 규칙: 목록 끝이 화면 아래 반 장 안에 들어오면 한 묶음씩 아래에 붙인다(보던 자리 그대로). 붙이는 동안 "불러오는 중…", 끝이면 "모든 릴리스를 다 봤어요", 실패하면 [다시 시도].
- 스크립트가 없으면 [더 보기]가 `?upto=`로 더 많이 그린다.
- /releases#v1.30.0처럼 아직 안 붙은 버전으로 오면 그 버전이 나올 때까지 불러 그리로 간다.
- 사진은 loading=lazy, 주소는 /releases/img/…(1.65.4).
- DB 바뀜 없음.

## Success Criteria
- 통합 테스트(ReleaseNotesPageIT): 첫 화면 10개, more 조각 10개, ?upto로 끝까지와 끝 문구.
- 첫 화면 HTML 약 33KB, 사진 39장.
