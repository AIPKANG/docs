# Feature Specification: 릴리스 노트 화면

**Feature Branch**: `071-release-notes` | **Created**: 2026-10-13 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-13 chan 15개 목록 10번 "릴리스노트 제작하기(어디에 넣을지는 고민중)" → 사이트 안 `/releases` 페이지 + 꼬리말 링크(추천안 A)로 결정, GitHub Releases만 쓰는 안은 사이트 밖으로 나가서 뺌).

## User Scenarios & Testing
### User Story 1 - 누구나 사이트에서 바뀐 내역을 본다 (P1)
모든 화면 꼬리말의 "릴리스 노트"를 누르면 `/releases`에서 버전별 변경 내역(저장소 `CHANGELOG.md` 그대로)을 본다. 로그인 없이 볼 수 있다.

## Requirements
- **FR-001** frontend 모듈 빌드가 저장소 맨 위 `CHANGELOG.md`를 jar의 `releases/CHANGELOG.md`로 넣는다. Dockerfile 빌드 단계에 `COPY CHANGELOG.md`.
- **FR-002** `ReleaseNotesController` GET `/releases`: 파일을 읽어 글 본문과 같은 `ContentRenderer`(정화 포함)로 HTML을 만들고 처음 한 번만 렌더링해 둔다. 맨 위 `# 릴리스 노트` 줄은 화면 h1로 대신하고, `025~036` 같은 범위가 취소선이 되지 않게 `~`를 글자로 바꾼다. 파일이 없으면 404.
- **FR-003** 꼬리말 링크(이용약관 · 개인정보 처리방침 · 릴리스 노트).
- **FR-004** 내용의 원본은 CHANGELOG.md 하나(릴리스 때 쓰는 그대로). 따로 관리하는 화면용 글은 없다.

## Success Criteria
- ReleaseNotesPageIT: `/releases` 200, h1 "릴리스 노트", `[1.0.0]`·`025~036` 포함, `<del>` 없음, 홈 꼬리말에 `/releases` 링크. 로컬 브라우저 확인.
