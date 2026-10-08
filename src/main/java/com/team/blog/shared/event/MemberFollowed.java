package com.team.blog.shared.event;

import java.time.Instant;

/** 팔로우가 실제로 생김(018 FR-005). 알림(017)이 커밋 후 구독한다. */
public record MemberFollowed(long followerId, long followeeId, Instant followedAt) implements DomainEvent {
}
