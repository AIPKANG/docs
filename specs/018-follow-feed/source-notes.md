# Source notes: 018-follow-feed

원문: `docs/24-follow-feed.md` (참고: `docs/20-domain-events.md` §3-4, `docs/42-permission-matrix.md` §10-1, `docs/44-withdraw.md` 정리 단계 표, `docs/10-post-list.md` §2·§4)

## plan 단계에서 참고할 기술 결정

- 화면 주소: `/@주소/followers`, `/@주소/following`, `/feed`(JS 없음: `/feed?cursor=…`) (24 §2-2, §2-3)
- API: `PUT|DELETE /api/members/{handle}/follow` → `200 { following, followerCount }`, `GET /api/members/{handle}/followers|following?cursor`, `GET /api/feed?cursor` (10 §4-2 형식) (24 §3). PUT/DELETE는 멱등성 때문
- 오류: 401 / 404 / 400 `CANNOT_FOLLOW_SELF` (24 §3)
- 저장: `INSERT … ON CONFLICT DO NOTHING RETURNING` / `DELETE … RETURNING` → 행이 돌아올 때만 `MemberFollowed`·`MemberUnfollowed` 발행 (24 §4, 20 EV-4)
- 대상 확인: `member.handle`로 찾고 `withdrawn_at`이면 404 (24 §4)
- 피드 쿼리: `follow` JOIN `post` JOIN `member`, `VisibilityFilter`(06 R-2/R-2a) + `withdrawn_at IS NULL`, 키셋 `(first_public_at, id) <`, `LIMIT 10` (24 §5)
- 팔로워 수는 그때그때 `COUNT`(탈퇴 신청 제외), 회원 행에 카운터 컬럼 두지 않음 (24 F-6, §5)
- 인덱스: 피드는 `ix_post_blog (author_id, first_public_at DESC, id DESC)` 병합 사용; 팔로우 여부는 `ANY(:ids)` 1번 (24 §5)
- 테이블 `follow(follower_id, followee_id, created_at)` PK 복합, `ck_follow_self`, `ix_follow_followee`, `ix_follow_follower` — V2 마이그레이션 (24 §10)
- 탈퇴 정리: `FollowWithdrawalPurgeStep`, 순서 65 (6 친구 관계 삭제 다음), `DELETE … WHERE follower_id = :me OR followee_id = :me` (24 §6, 44 표)
- 낙관적 갱신 버튼 (24 §2-1)
- 미결: 공통 IP 요청 제한 포함 여부 (24 §12 화요일 안건 2) → spec FR-020
