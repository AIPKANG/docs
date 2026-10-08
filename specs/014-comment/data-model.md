# Data Model: 댓글 (014)
새 마이그레이션 없음. V1 `comment`(`parent_id` 항상 최상위, 복합 FK `(post_id, parent_id)` CASCADE, `reply_to_member_id`, `deleted_at`(자리), `hidden_*`, CHECK `ck_comment_content`·`ck_comment_reply_to`·`ck_comment_edited`), `post.comment_count`.
상태: NORMAL / DELETED(자리) / HIDDEN / WITHDRAWN_AUTHOR. 댓글 수 = 삭제·숨김 아닌 행 수.
Redis: `cmt:dedupe:{memberId}:{postId}:{hash}`(10초), 제한 `comment:create:{id}`(1분 10), `comment:edit:{id}`(1분 20).
사건: `CommentCreated(commentId, postId, authorId, parentId, replyToMemberId)`, `CommentDeleted(commentId, postId)`.
오류: `COMMENT_REQUIRED`, `COMMENT_TOO_LONG`(400), `REPLY_TARGET_UNAVAILABLE`(400), `COMMENT_HIDDEN`(409), 404, 401, 403, 429.
