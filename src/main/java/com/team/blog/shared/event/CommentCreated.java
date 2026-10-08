package com.team.blog.shared.event;

/** 댓글·답글이 생김(014). 알림(017)이 커밋 후 구독한다. {@code replyToMemberId}는 답글의 답글일 때만. */
public record CommentCreated(long commentId, long postId, long authorId, Long parentId, Long replyToMemberId)
        implements DomainEvent {
}
