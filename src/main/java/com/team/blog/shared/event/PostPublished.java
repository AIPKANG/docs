package com.team.blog.shared.event;

import java.time.Instant;

/** 글이 처음 발행됨(005 FR-017). 알림·검색 색인·sitemap이 커밋 후 구독한다. */
public record PostPublished(long postId, long authorId, String visibility, Instant firstPublicAt) implements DomainEvent {
}
