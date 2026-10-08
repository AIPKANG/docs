package com.team.blog.notification.application;

import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportsResolved;
import com.team.blog.shared.event.FriendRequestClosed;
import com.team.blog.shared.event.FriendRequested;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.event.PostUnliked;
import com.team.blog.shared.event.PostVisibilityChanged;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 사건 → 알림(25 §4, 20 §4). 커밋 뒤에만 받고(롤백이면 없음), 처리는 {@link NotificationDispatcher}가 요청과 떼어 한다.
 * 사건에는 식별자만 있어 받는 사람·글 상태는 처리할 때 다시 읽는다. 신고 결과·숨김(022)은 운영 알림(끌 수 없음, 행동한 사람 없음)이다.
 */
@Component
public class NotificationListeners {

    private final NotificationWriter writer;
    private final NotificationDispatcher dispatcher;
    private final JdbcTemplate jdbc;

    public NotificationListeners(NotificationWriter writer, NotificationDispatcher dispatcher, JdbcTemplate jdbc) {
        this.writer = writer;
        this.dispatcher = dispatcher;
        this.jdbc = jdbc;
    }

    private long postAuthor(long postId) {
        return jdbc.queryForObject("SELECT author_id FROM post WHERE id = ?", Long.class, postId);
    }

    /** 글 작성자에게 댓글, 답한 댓글의 작성자에게 답글. 둘이 같으면 답글만(FR-005). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CommentCreated e) {
        dispatcher.submit("comment " + e.commentId(), () -> {
            long postAuthor = postAuthor(e.postId());
            Long replyReceiver = e.parentId() == null ? null : e.replyTargetAuthorId();
            if (replyReceiver != null) {
                writer.single(replyReceiver, NotificationType.REPLY, e.postId(), e.commentId(), e.authorId());
            }
            if (replyReceiver == null || replyReceiver != postAuthor) {
                writer.single(postAuthor, NotificationType.COMMENT, e.postId(), e.commentId(), e.authorId());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CommentDeleted e) {
        dispatcher.submit("comment deleted " + e.commentId(), () -> writer.deleteForComment(e.commentId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PostLiked e) {
        dispatcher.submit("like " + e.postId(), () -> writer.addToGroup(e.authorId(), NotificationType.LIKE,
                NotificationWriter.likeGroup(e.postId()), e.postId(), e.likerId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PostUnliked e) {
        dispatcher.submit("unlike " + e.postId(), () -> writer.removeFromGroup(postAuthor(e.postId()),
                NotificationWriter.likeGroup(e.postId()), e.likerId()));
    }

    /** 처음 전체 공개될 때 한 번(FR-006): 공개로 발행했거나, 공개 범위 변경으로 처음 공개됐을 때. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PostPublished e) {
        if ("PUBLIC".equals(e.visibility())) {
            dispatcher.submit("new post " + e.postId(), () -> writer.newPost(e.postId(), e.authorId()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PostVisibilityChanged e) {
        if (e.firstPublic()) {
            dispatcher.submit("new post " + e.postId(), () -> {
                if ("FRIENDS".equals(e.from())) {
                    writer.firstPublicCheer(e.postId(), e.authorId()); // 026 강성찬 개인 확장
                }
                writer.newPost(e.postId(), e.authorId());
            });
        }
    }

    /** 018: 새 팔로워 묶음(7일에 한 번), 언팔로우는 안 읽은 묶음에서만 뺀다(상대에게 알리지 않음). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MemberFollowed e) {
        dispatcher.submit("follow " + e.followeeId(), () -> writer.addToGroup(e.followeeId(), NotificationType.FOLLOW,
                NotificationWriter.FOLLOW_GROUP, null, e.followerId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MemberUnfollowed e) {
        dispatcher.submit("unfollow " + e.followeeId(), () -> writer.removeFromGroup(e.followeeId(),
                NotificationWriter.FOLLOW_GROUP, e.followerId()));
    }

    /** 025 친구 요청: 받는 사람에게 묶음 알림. 수락·거절·취소는 그 사람을 묶음에서 뺄 뿐 새 알림이 없다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FriendRequested e) {
        dispatcher.submit("friend " + e.receiverId(), () -> writer.addToGroup(e.receiverId(), NotificationType.FRIEND_REQUEST,
                NotificationWriter.FRIEND_GROUP, null, e.requesterId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FriendRequestClosed e) {
        dispatcher.submit("friend-closed " + e.receiverId(), () -> writer.removeFromGroup(e.receiverId(),
                NotificationWriter.FRIEND_GROUP, e.requesterId()));
    }

    /** 022: 숨김 → 작성자에게 1건(신고자·관리자 정보 없음). 댓글이면 그 댓글로 생긴 댓글·답글 알림을 지운다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ContentHidden e) {
        dispatcher.submit("hidden " + e.postId(), () -> {
            if (e.commentId() != null) {
                writer.deleteForComment(e.commentId());
            }
            writer.operational(e.authorId(), NotificationType.CONTENT_HIDDEN, e.postId(), e.commentId(), null, null);
        });
    }

    /** 022: 신고마다(=신고자마다) 처리 결과 1건. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ReportsResolved e) {
        dispatcher.submit("reports resolved", () -> e.reports().forEach(r ->
                writer.operational(r.reporterId(), NotificationType.REPORT_RESOLVED, null, null, r.reportId(), e.result())));
    }
}
