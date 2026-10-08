package com.team.blog.notification.application;

import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
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
 * 사건에는 식별자만 있어 받는 사람·글 상태는 처리할 때 다시 읽는다. 팔로우(018)·신고·숨김(022) 사건은 그 기능이 여기에 더한다.
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
            dispatcher.submit("new post " + e.postId(), () -> writer.newPost(e.postId(), e.authorId()));
        }
    }
}
