# Data Model: 팔로우·팔로잉 피드 (018)
새 마이그레이션 없음. V1 `follow(follower_id, followee_id) PK, created_at, ck_follow_self, ix_follow_followee, ix_follow_follower`. 사건 `MemberFollowed(followerId, followeeId, followedAt)`, `MemberUnfollowed(followerId, followeeId)`. 오류 400 `CANNOT_FOLLOW_SELF`. Redis `rate:follow:ip:{ip}`. 설정 `blog.rate-limit.per-ip-per-minute`, `blog.follow.*`.
