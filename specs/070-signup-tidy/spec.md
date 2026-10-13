# Feature Specification: 가입 화면 덜어내기

**Feature Branch**: `070-signup-tidy` | **Created**: 2026-10-13 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-13 chan 15개 목록 12번 "회원가입창 너무 난잡한 느낌? 좋은 방법있는지 확인 후 요청" → 세 안 중 추천안 A "덜어내기"로 결정). 로그인에는 카카오가 없고 Google·GitHub뿐이며, 가입 화면에는 소셜 버튼이 없어 난잡함의 원인은 폼 안내 글이 한꺼번에 보이는 것으로 봤다. 칸·검증·주소·서버 동작은 그대로.

## User Scenarios & Testing
### User Story 1 - 가입 화면이 로그인과 같은 모양이고 꼭 필요한 안내만 보인다 (P1)
`/signup`은 로그인처럼 왼쪽 그림 + 오른쪽 폼 카드다(좁은 화면은 그림이 위). 비밀번호 규칙 6줄은 비밀번호 칸을 누르거나 입력했을 때만 보이고, 블로그 주소 안내와 "가입 후 바꿀 수 없어요" 경고는 한 줄이다. 약관은 "모두 동의해요" 한 번으로 두 필수 동의를 함께 켤 수 있다.

## Requirements
- **FR-001** signup.html을 `.auth-card`·`.auth-art`·`.auth-panel` 구조로(로그인과 같음), "이미 계정이 있나요? 로그인"은 폼 아래 `.auth-links`.
- **FR-002** 비밀번호 칸에 placeholder "8~16자, 영문·숫자·특수문자", 규칙 목록은 `.signup-password`가 포커스도 입력도 없을 때 CSS로 숨김(스크립트 불필요). 서버가 돌려준 비밀번호 오류는 따로 그대로 보인다.
- **FR-003** 주소 도움말과 경고를 `#handle-help` 한 줄로 합친다.
- **FR-004** `data-agree-all` 칸: 켜고 끄면 같은 fieldset의 필수 동의 칸(`agreeTerms`·`agreePrivacy`)이 같이 바뀌고, 개별 칸을 바꾸면 모두 동의 표시가 따라간다(signup-form.js). 개별 동의 칸은 개인정보 수집·이용 동의를 따로 받기 위해 그대로 둔다. 이용약관 보기 링크 추가.
- **FR-005** 소셜 가입 화면(social-signup.html)은 이번 범위 밖.

## Success Criteria
- ReleaseNotesPageIT: 가입 화면에 `data-agree-all`과 두 동의 칸. PrivacyPageIT·EmailSignupIT 그대로 통과. 로컬 브라우저에서 넓은·좁은 화면, 비밀번호 칸 포커스 때 규칙 표시, 모두 동의 켜고 끄기 확인.
