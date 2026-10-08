package com.team.blog.shared.event;

/** 댓글이 삭제됨(자리만 남은 경우 포함, 014 FR-027). 알림(017)이 그 댓글로 생긴 알림을 지운다. */
public record CommentDeleted(long commentId, long postId) implements DomainEvent {
}
