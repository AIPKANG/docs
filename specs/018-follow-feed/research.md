# Research: 팔로우·팔로잉 피드 (018-follow-feed)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/24-follow-feed.md`, `docs/42-permission-matrix.md` §10-1, 017 알림

사용자 지시: 질문 없이 기본값.

## R-1. 팔로우 (FR-001~FR-006, FR-019, FR-020)
- 순서: 공통 IP 제한(429) → 로그인(401, 인증 전 허용) → 대상(`BlogOwnerResolver`, 없음·탈퇴 유예 404) → 자기 자신(400 `CANNOT_FOLLOW_SELF`, DB `ck_follow_self`도). `INSERT … ON CONFLICT DO NOTHING RETURNING` / `DELETE … RETURNING`으로 행이 바뀐 경우만 `MemberFollowed`/`MemberUnfollowed`. 응답 `{following, followerCount}`(탈퇴 유예 제외 수).
- 공통 IP 요청 제한 설정이 아직 없어 `blog.rate-limit.per-ip-per-minute`(120)을 새로 두고 팔로우가 이 값을 그대로 쓴다(`blog.follow.per-ip-per-minute: ${…}`). 다른 기능이 공통 제한을 만들면 같은 값을 쓴다(U-1).
- 알림: `MemberFollowed` → 017 `FOLLOW` 묶음(7일), `MemberUnfollowed` → 안 읽은 묶음에서만 빼기(상대에게 알리지 않음).

## R-2. 수·목록 (FR-007~FR-010)
- 수: 탈퇴 유예 회원 제외 서브쿼리 2개를 한 번에. 목록: 회원 JOIN + 보는 사람 `followedByMe` EXISTS를 한 쿼리, `(follow.created_at, member.id) DESC` 커서(010 `FeedCursor` 재사용), 소개는 첫 줄만.
- 버튼 조각 `fragments/follow :: button`: 비회원 로그인 링크(원래 화면으로), 회원 폼(`POST /@주소/follow`, `back`은 같은 사이트 경로만) + `follow.js` 낙관적 갱신·실패 되돌림·"잠시 후 다시 시도해 주세요", 팔로우 중 "팔로잉 ✓"(마우스·초점 "언팔로우"). 내 블로그·내 글·목록의 나 자신에는 없음.
- 017 새 팔로워 알림 이동 `/me/followers` → 303 `/@내주소/followers`.

## R-3. 피드 (FR-011~FR-017)
- `PostListQuery.followingFeed`: 006 공용 목록 조건 + `author_id IN (SELECT followee_id FROM follow WHERE follower_id = ?)`, `first_public_at DESC, id DESC`, 9개(10개 읽기), 커서. `ix_follow_follower`와 `ix_post_feed` 사용. 홈과 같은 카드·`list-more.js`(뒤로 가기 복원)·`/feed?cursor=` 링크. 빈 상태 두 가지.
- 상단 메뉴 [피드]는 로그인 때만.

## R-4. 탈퇴 (FR-018)
- 유예 중에는 관계를 지우지 않고 수·목록·피드 조건에서 뺀다. 30일 정리 단계 `FollowService.purgeWithdrawn`(양방향 삭제)은 023이 부른다.

## 남은 확인
- U-1: 공통 IP 요청 제한의 기준값(120/분은 임시 기본값).

## 구현 메모 (2026-10-08)
- `FollowIT` 4개(멱등·동시 20 → 1·판정 순서·DB CHECK, 목록·수·탈퇴 제외·복구·커서·화면·폼·`/me/followers`·정리, 피드 조건·커서 중복 없음·언팔로우·빈 상태·메뉴, IP 제한 429), 매트릭스 §10-1.
