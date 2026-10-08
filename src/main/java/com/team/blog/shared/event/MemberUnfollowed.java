package com.team.blog.shared.event;

/** 팔로우가 실제로 없어짐(018 FR-005). 상대에게 알리지 않고, 안 읽은 새 팔로워 묶음에서만 뺀다(017 FR-013). */
public record MemberUnfollowed(long followerId, long followeeId) implements DomainEvent {
}
