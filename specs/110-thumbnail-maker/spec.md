# Feature Specification: 썸네일 만들기

**Feature Branch**: `110-thumbnail-maker` | **Created**: 2026-10-16 | **Status**: Implemented (blog 1.66.0 화면, 1.66.1 AI 연결 도구)

**Scope**: **강성찬 개인 확장**.

## 결정
- 발행 설정의 카드 대표 사진에 [썸네일 만들기]. 창에서 넣을 제목을 고치고 배경 4가지(초록·크림·노을·먹색) 중 하나를 고르면 미리 보기가 바뀐다. [대표 사진으로 저장]하면 그림(1600×1000 PNG)을 내 사진으로 올리고 이 글의 카드 대표 사진으로 정한다.
- 그림에는 시리즈 이름(있으면), 제목, @아이디와 오늘 날짜가 들어간다. 글자 색은 배경과 대비 4.5:1 이상.
- AI에 연결하기에 `make_thumbnail(post_id, theme)`: 같은 배치를 서버가 그려 카드 대표 사진으로 정한다. 내 임시글만 되고 발행한 글·남의 글은 오류.
- 서버 그림에는 Pretendard(OFL)를 앱에 넣어 쓰고, 실행 이미지에 fontconfig·libfreetype6을 둔다.
- DB 바뀜 없음.

## Success Criteria
- 화면: thumbnail.test.mjs(띄어쓰기 단위 줄바꿈, 긴 단어 끊기, 넘치면 …, 빈 제목).
- 서버: ThumbnailRendererTest(배경 위 한글 제목), McpIT(내 임시글만, 1600×1000 PNG가 카드 대표 사진).
- 운영과 같은 Docker 이미지에서 한글이 네모로 깨지지 않음(1.66.1 배포 전 확인).
