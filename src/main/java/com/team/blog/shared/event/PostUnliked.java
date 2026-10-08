package com.team.blog.shared.event;

/** 좋아요가 실제로 지워짐(015 FR-012). */
public record PostUnliked(long postId, long likerId) implements DomainEvent {
}
