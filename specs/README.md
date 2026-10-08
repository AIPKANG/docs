# 기능 명세 목록

`docs/`의 설계 문서를 Spec Kit 명세로 다시 정리한 목록입니다. 원문 결정 근거는 각 명세의 `Input`과
`source-notes.md`에 적힌 `docs/` 절을 보세요. ERD(`docs/03`, `docs/51`, `docs/52`)는 명세가 아니라
`/speckit-plan` 단계의 데이터 모델 입력으로 씁니다.

| 명세 | 원문 | Tier |
|---|---|---|
| [001-auth](./001-auth/spec.md) 로그인·로그아웃 | 07 | A |
| [002-blog-address-nickname](./002-blog-address-nickname/spec.md) 블로그 주소·닉네임 | 08, 09 | A |
| [003-profile](./003-profile/spec.md) 프로필 수정 | 11 | A |
| [004-draft-autosave](./004-draft-autosave/spec.md) 임시저장·자동 저장 | 04 | A |
| [005-post-publish](./005-post-publish/spec.md) 발행·수정 | 05 | A |
| [006-post-visibility](./006-post-visibility/spec.md) 공개 범위 | 06 | A |
| [007-content-sanitize](./007-content-sanitize/spec.md) 본문 렌더링·정화 | 12 | A |
| [008-image-upload](./008-image-upload/spec.md) 이미지 업로드 | 04, 23 | B |
| [009-post-list-blog](./009-post-list-blog/spec.md) 전체 글 목록·개인 블로그 | 10 | A |
| [010-post-detail](./010-post-detail/spec.md) 글 상세 | 40 | A |
| [011-manage-posts-trash](./011-manage-posts-trash/spec.md) 내 글 관리·휴지통 | 41, 13 | A |
| [012-access-permission](./012-access-permission/spec.md) 권한 매트릭스 | 42 | A (공통 규칙) |
| [013-tag](./013-tag/spec.md) 태그 | 22 | B |
| [014-comment](./014-comment/spec.md) 댓글·답글 | 21 | B |
| [015-like](./015-like/spec.md) 좋아요 | 30 | B |
| [016-view-count](./016-view-count/spec.md) 조회수 | 31 | B |
| [017-notification](./017-notification/spec.md) 알림·도메인 이벤트 | 25, 20 | C |
| [018-follow-feed](./018-follow-feed/spec.md) 팔로우·피드 | 24 | C |
| [019-trending](./019-trending/spec.md) 트렌딩 | 32 | C |
| [020-search](./020-search/spec.md) 검색 | 33 | C |
| [021-ai-tag-suggest](./021-ai-tag-suggest/spec.md) AI 태그 추천 | 34 | C |
| [022-report-hide](./022-report-hide/spec.md) 신고·숨김 | 43 | C |
| [023-account-withdraw](./023-account-withdraw/spec.md) 회원 탈퇴 | 44, 13 | C |
| [024-dark-mode](./024-dark-mode/spec.md) 다크 모드 | 45 | C |

## Clarify 결정 (2026-10-07)

| 명세 | 질문 | 결정 |
|---|---|---|
| 001 | 소셜 첫 로그인 때 같은 이메일의 다른 수단 계정이 있으면 안내할지 | "기존 계정으로 로그인 / 새 계정 만들기" 안내를 보여준다 (계정은 합치지 않음) |
| 010, 016 | 관리자가 신고 처리로 연 조회를 조회수에서 뺄지 | 뺀다 |
| 018 | 팔로우를 공통 IP 요청 제한에 포함할지 | 포함, 공통 기준 사용 |
| 019 | 숨긴 댓글을 트렌딩 점수에서 뺄지 | 뺀다 |
| 020 | 검색어 `#spring` 처리 | 태그가 있으면 태그 페이지, 없으면 `#`을 뺀 일반 검색 |
| 021 | 비공개·친구 공개 글도 외부 AI로 보낼지 | 보내지 않는다. 공개 글만 태그 추천 |
| 공통 | 화면 구성 (이 저장소) | Thymeleaf 서버 렌더링 + 세션 |

## 문서끼리 어긋난 점 (명세에서 정한 쪽)

| 내용 | 어긋남 | 명세의 선택 |
|---|---|---|
| 파일 저장소 | 23은 RustFS, 01 결정 기록(10-06)은 MinIO | MinIO |
| 내 글 좋아요 | 30은 403, 42는 400 `CANNOT_LIKE_OWN_POST` | **결정(10-07): 400.** 409는 상태 충돌 전용이라 쓰지 않음. 30 문서 수정을 김민서 님께 요청 |
| 판정 순서 | 30·05는 글 존재를 먼저, 42 §3은 로그인·계정 상태를 먼저 | 42 §3 |
| 42 내부 | §7 표의 "볼 수 없는 글 × 인증 전 = 404"가 §3 순서와 다름 | §3 (403) |
| 남의 글에 AI 태그 요청 | 34는 403, 42는 404 | 42 |
| AI 태그 형식 | 34 프롬프트는 영문 소문자·하이픈만, 22는 한글 등 허용 | 22 |
| 인증 전 막는 행동 | 07은 글·댓글·사진, 42는 좋아요·신고도 | 42 |
| 빈 임시글 정리 | 결정 기록은 생성 24시간, 04는 마지막 수정 24시간·자동 저장 없음도 | 04 (더 엄격) |
| 탈퇴 익명화 | 13은 `profile_image_url`, 11·결정 기록은 `profile_image_id`, 44가 정리 단계 추가 | 44 |
| 숨긴 글 목록 제외 | 43이 공통 공개 범위 필터 변경 제안 (화요일 회의) | **결정(10-07): 필터 변경은 확정, 적용은 022 구현 때.** 작성자 본인에게는 보임 |
| 직접 업로드 CSP | 12의 CSP에 저장소 주소(`connect-src`) 없음 | plan에서 맞춤 |
| 신고 이벤트 | 20에 `MEMBER` 대상·`MemberSuspended` 관련 차이 | 43 요청으로 남김 |
| 처리방침 화면 | 016 FR-018·021 FR-035 안내할 곳 없음 | **결정(10-08): `/privacy` 초안 추가**, 표현은 팀이 다듬음 |
| Gemini 모델·하루 한도 | 34는 AI Studio 확인 | **결정(10-08): 설정값 유지, 배포 직전 실제 한도 90%로** |
| 공통 IP 요청 제한 | 공통 설정 없음 | **결정(10-08): 120/분 유지**, 공통 제한 생기면 같은 설정 |
| 신고 복사본 주소·공개 범위 | 43 요구, V1 칸 없음 | **결정(10-08): 칸 추가 안 함**(주소는 불변, 작성자 번호로 표시) |
| 코드 문법 강조 | 45는 두 구성 요구, 렌더러에 강조 없음 | **적용(10-08)**: highlight.js 11.11.1을 webjar로 우리 서버에서 제공(CSP 그대로), 글 상세·미리보기, 색은 theme.css `--code-*` 라이트·다크 |
| 001 소셜 로그인·Gmail 실제 확인 | 키 필요 | 배포 준비 때 키를 넣고 확인 |
