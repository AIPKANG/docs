package com.team.blog.shared.event;

import java.time.Instant;

/** 친구 요청이 새로 생김(025). 받는 사람에게 알림(017)이 간다. */
public record FriendRequested(long requesterId, long receiverId, Instant requestedAt) implements DomainEvent {
}
