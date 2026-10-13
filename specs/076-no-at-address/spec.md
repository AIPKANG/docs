# Feature Specification: 블로그 주소에서 @ 빼기

**Feature Branch**: `076-no-at-address` | **Created**: 2026-10-13 | **Status**: Implemented

**Scope**: **강성찬 개인 확장** (10-13 chan "블로그 주소가 /@로 되있는데 … 필요 없다면 없애줘, 닉네임 뒤에 오는 꼬리표 느낌", 카드에서 "@ 빼기" 선택).

## 결정
- 블로그 주소는 `geulgreen.java21.net/아이디`, 글은 `/아이디/posts/번호`처럼 그 아래 주소도 같은 방식.
- 예전 `/@아이디/...` GET·HEAD는 새 주소로 **301**(나머지 경로·쿼리 유지). 예전 주소로 보내는 폼 POST(팔로우·댓글 등)는 그대로 받는다.
- 074의 옛 아이디 넘김은 `/@옛아이디`, `/옛아이디` 둘 다 새 주소로 간다.
- 사이트가 쓰는 맨 앞 경로는 아이디로 쓸 수 없다(가입·아이디 변경 모두). 새로 예약: account, actuator, auth, error, feed, missions, notes, oauth2, password, posts, releases, rss, test, withdrawn, css, fonts, webjars, media(기존 예약어 about·login·signup·settings·api·tags·admin 등은 그대로).
- 076 전에 생긴 아이디가 사이트 경로·예약어와 같으면 그 블로그는 예전 `/@아이디`로 계속 열리고(넘기지 않음), 링크도 `/@아이디`로 만든다. 링크는 `BlogPaths`(화면은 `${paths.blog(아이디)}`) 한 곳에서 만든다. 스크립트가 그리는 카드·댓글 작성자 링크는 `/@아이디`(보통 아이디는 301로 새 주소).
- 화면에 보이는 "@아이디" 표시는 이 명세 범위 밖(075가 다룸).
- 학교 서버 Nginx는 `location /` 전체를 앱으로 넘기므로 바꾸지 않는다.

## Requirements
- **FR-001** `HandlePathCanonicalizer`(가장 앞 필터): 맨 앞 칸이 주소 형식이고 예약어가 아니면 안쪽에서 `/@{handle}/**`로 바꿔 넘긴다(컨트롤러·인터셉터 경로는 그대로). `/@...` GET·HEAD는 301, 대문자 주소는 소문자로 301.
- **FR-002** 서버가 만드는 링크(글 주소·작성자 링크·되돌아가기·RSS·사이트맵·canonical·OG·알림·관리 화면)와 템플릿·스크립트 링크는 `/아이디`.
- **FR-003** `RootPathReservedIT`: 컨트롤러·정적 자원·보안 경로의 맨 앞 칸 중 주소 형식인 것이 예약어에 없으면 실패.

## Success Criteria
- AddressWithoutAtIT: `/@주소/posts/1?x` → 301 `/주소/posts/1?x`, `/@Kim` → `/kim`, 블로그 화면 링크에 `/@` 없음, 예전 주소로 팔로우 POST 됨, `/login`은 그대로, 없는 주소 404.
- 기존 테스트는 새 주소 기준으로 통과.

## 알려진 한계
- 글 본문에 직접 쓴 `/@아이디` 링크는 바꾸지 않는다(301로 열림).
