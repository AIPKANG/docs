# Data Model: 좋아요 (015)
새 마이그레이션 없음. `post_like(post_id, member_id) PK, created_at`, `post.like_count`. Redis `like:member:{id}`(1분 60). 사건 `PostLiked(postId, likerId, authorId, likedAt)`, `PostUnliked(postId, likerId)`. 오류 400 `CANNOT_LIKE_OWN_POST`.
