# Feature Specification: 내 글은 발행 글 먼저, 설정은 섹션 메뉴로

**Feature Branch**: `063-settings-sections` | **Created**: 2026-10-12 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-12 chan 요청 "내 글에서 임시글보다 내글이 더 앞에 나오게 바꿔줘 그리고 설정에서 여러개가 위아래로 나열되어있는데 그거 옆에 섹션별로 묶어주고 누르면 나오는 형식으로 바꿔서 가독성 좋게 만들자"). 화면 배치만 바꾸고 기능·주소·API는 그대로.

## User Scenarios & Testing
### User Story 1 - 내 글을 열면 발행 글이 먼저 (P1)
내 글 관리 탭은 발행 글 · 임시글 · 휴지통 · 통계 순서이고, 탭 없이 열면(머리말 "내 글") 발행 글 탭이 열린다.

### User Story 2 - 설정을 섹션별로 골라 본다 (P1)
설정 화면 왼쪽(휴대폰은 위쪽 가로 줄)에 섹션 메뉴가 있고, 누르면 그 섹션만 보인다: 프로필 / 계정(이메일·로그인 수단·비밀번호·새 글 기본 공개 범위·회원 탈퇴) / 블로그·사진(카테고리·시리즈 안내, 사진 저장 공간) / 친구 / 화면(화면 테마) / 알림 / AI.

## Requirements
- **FR-001** `ManageTab` 기본값(탭 값이 없거나 틀릴 때)은 PUBLISHED. `/api/me/posts`도 같은 기본값을 따른다. 탭 줄은 발행 글이 임시글 앞.
- **FR-002** 설정 화면의 기존 섹션을 7개 묶음(`.settings-panel`, id `settings-*`)으로 감싸고 `.settings-nav` 링크(#settings-*)를 둔다. 넓은 화면(720px 이상)은 180px 세로 메뉴 + 내용 2단, 좁은 화면은 가로로 넘기는 줄.
- **FR-003** settings-sections.js: 고른 묶음만 보이고(나머지 hidden), 주소 #이 가리키는 요소가 든 묶음을 연다(알림 저장 뒤 `#notification-heading` → 알림). 스크립트가 없으면 모든 묶음이 펼쳐진다. 기존 폼·스크립트·id는 그대로.
- **FR-004** 색은 기존 토큰만 쓴다.

## Success Criteria
- ManagePostsIT: 탭 없이 열면 발행 글, 발행 글 탭이 임시글 탭 앞. 임시글을 보던 검사(ManagePostsIT·EditorPageIT·EmptyDraftLeaveIT)는 tab=drafts를 명시.
- ThemeIT: 설정에 섹션 메뉴와 스크립트가 있고 화면 테마가 [화면] 묶음 안. 로컬 브라우저에서 넓은·좁은 화면 확인.
