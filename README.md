# 팀 블로그 플랫폼 — 문서 저장소

여러 사람이 각자 블로그를 갖고 글을 쓰는 서비스(티스토리·벨로그 같은 형태)의 **설계 문서와 기능 명세**를 모은 저장소입니다.
소스 코드·빌드·배포는 소스 저장소 **[practicech/blog](https://github.com/practicech/blog)** 에 있고, 운영 사이트는 https://geulgreen.java21.net 입니다.

- 명세 001~024: 팀(강성찬·나민서·김민서) **공통 최소 요구사항**(v1.0.0)
- 명세 025~: **강성찬 개인 확장**(팀은 공통만 맞추고 각자 추가만 하기로 함, 공통 완료 기준은 그대로 지킴)
- 릴리스별 변경 내역: 소스 저장소의 [CHANGELOG.md](https://github.com/practicech/blog/blob/main/CHANGELOG.md)

## 폴더

| 경로 | 내용 |
|---|---|
| [`docs/`](docs/) | 팀이 쓴 원본 설계 문서(요구사항·아키텍처·ERD·기능별 설계). 결정 근거의 원문이라 고치지 않는다 |
| [`specs/README.md`](specs/README.md) | 문서 간 차이와 팀 결정 기록, 개인 확장 목록 |
| `specs/NNN-기능/` | 기능별 `spec.md`(명세) → `plan.md`·`research.md`(설계·결정) → `tasks.md`(작업) |
| [`specs/personal-extensions.md`](specs/personal-extensions.md) | 강성찬 개인 확장 후보·순서 |
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | 프로젝트 헌법(원칙) |
| `.specify/`, `.claude/skills/` | [GitHub Spec Kit](https://github.com/github/spec-kit) 템플릿·스크립트·명령 |
| Crowfoot ERD | https://crowfoot.java21.net/workspaces/62/models/672 (테이블 20개·관계 41개, 명세 001~024와 연결) |

## 명세 목록

| 구분 | 명세 |
|---|---|
| 팀 공통(001~024) | 001 가입·로그인 · 002 블로그 주소·닉네임 · 003 프로필 · 004 자동 저장 · 005 발행 · 006 공개 범위 · 007 Markdown 정화 · 008 사진 · 009 글 목록 · 010 글 상세 · 011 내 글 관리·휴지통 · 012 접근 권한 · 013 태그 · 014 댓글 · 015 좋아요 · 016 조회수 · 017 알림 · 018 팔로우·피드 · 019 트렌딩 · 020 검색 · 021 AI 태그 추천 · 022 신고·숨김 · 023 탈퇴 · 024 다크 모드 |
| 강성찬 개인 확장(025~) | 025 친구 공개 · 026 공개 전환 · 027 조회수 30분 · 028 답글 깊이 · 029 링크 공개 · 030 그룹 공개 · 031 잔디 · 032 작성자 통계 · 033 짧은 기록 · 034 같은 주제(→057) · 035 AI 티저 · 036 새로 온 작가 · 037 카테고리 · 038 이전·다음 글 · 039 목차 · 040 방명록 · 041 시리즈 · 042 예약 발행 · 043 비밀 댓글 · 044 RSS · 045~050 관리자 화면 · 051 방문자 수 · 052 읽기 진행 막대 · 053 공유하기 · 054 글 고정 · 055 카드 대표 사진 · 056 태그 아이콘 · 057 오늘의 미션 · 058 서식 창 · 059 AI로 글 다듬기 · 060 보이는 대로 쓰기 · 061 화면 테마를 개인 설정으로 |

## 작업 방식

기능마다 Spec Kit 순서로 진행합니다: `/speckit-specify` 명세 → `/speckit-clarify`(선택) → `/speckit-plan` 계획 → `/speckit-tasks` 작업 → `/speckit-implement` 구현.
명세·계획은 이 저장소에, 구현은 소스 저장소에 올리고, 개인 확장은 명세 머리와 CHANGELOG에 "강성찬 개인 확장"으로 적습니다.
