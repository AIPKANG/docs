package com.team.blog.shared.event;

/** 발행한 글을 다시 발행함(005 FR-017). */
public record PostEdited(long postId, long authorId, String visibility) implements DomainEvent {
}
