# Feature Specification: 표 넣기

**Feature Branch**: `065-table-insert` | **Created**: 2026-10-13 | **Status**: Implemented (1.17.0)

**Scope**: **강성찬 개인 확장** (10-13 chan 남은 일 6번 "표 넣기 버튼"). 글쓰기 아래 줄에 [표] 버튼을 두고, 064 `/표`로도 넣는다. 저장은 GFM 표 Markdown이라 렌더러(007, TablesExtension)·정화기는 그대로다.

## User Scenarios & Testing
### User Story 1 - 표를 넣고 채우기 (P1)
[표]를 누르면 커서 자리에 3칸 표(머리 줄 + 칸 줄 두 개)가 들어가고 커서는 첫 머리 칸에 있다. 보이는 대로 쓰기 칸에서 Tab은 다음 칸, Shift+Tab은 앞 칸, 마지막 칸에서 Tab은 줄을 하나 더한다. Enter는 아래 칸, 마지막 줄에서는 표 밑 문단으로 간다.

## Requirements
- **FR-001** 보이는 대로 쓰기 칸: 마지막 커서 자리 문단을 나눠 표를 넣는다. 목록·표·코드·인용 안이면 그 덩어리 밑에 넣는다. 표 밑에는 계속 쓸 빈 문단을 둔다.
- **FR-002** Markdown 칸: 빈 GFM 표(`|  |  |  |` + `| --- | --- | --- |` + 빈 줄 둘)를 앞뒤 빈 줄로 띄운 제 줄에 넣고, 커서는 첫 칸 가운데.
- **FR-003** GFM 표 칸에는 줄바꿈이 없으므로 칸 안에서는 줄을 나누지 않는다(Enter는 아래 칸, Shift+Enter는 무시). Markdown으로 옮길 때 칸 안 줄바꿈은 띄어쓰기로, 칸 안 `|`는 `\|`로 바꾼다.
- **FR-004** 빈 칸도 누르기 쉽게 쓰는 칸의 칸에 최소 크기를 둔다.

## Success Criteria
- 렌더러 검사: 글쓰기가 넣는 빈 표와 칸 안 `\|`가 표로 그려진다(ContentRendererSyntaxTest). 자체 검사에 표 Markdown·제 줄에 넣기. 글쓰기 화면에 [표](EditorPageIT).
- 로컬 브라우저: 두 칸에서 [표]·`/표`, Tab으로 줄 더하기, Enter로 표 밖으로, 다시 그린 표의 빈 칸 채우기, Markdown 결과 확인.
