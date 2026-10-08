package com.team.blog.shared.event;

/**
 * 댓글·답글이 생김(014). 알림(017)이 커밋 후 구독한다. {@code replyToMemberId}는 답글의 답글일 때만(014 저장 값),
 * {@code replyTargetAuthorId}는 답글이면 답한 댓글의 작성자(017 답글 알림 받는 사람).
 */
public record CommentCreated(long commentId, long postId, long authorId, Long parentId, Long replyToMemberId,
                             Long replyTargetAuthorId) implements DomainEvent {
}
