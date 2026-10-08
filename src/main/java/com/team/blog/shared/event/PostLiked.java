package com.team.blog.shared.event;

import java.time.Instant;

/** 좋아요가 실제로 생김(015 FR-012). 알림·트렌딩이 커밋 후 구독한다. */
public record PostLiked(long postId, long likerId, long authorId, Instant likedAt) implements DomainEvent {
}
