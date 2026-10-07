package com.team.blog.shared.event;

import java.time.Instant;

/** 공개 범위가 바뀜(006 FR-021). 검색 색인·sitemap·알림이 커밋 후 구독한다. */
public record PostVisibilityChanged(long postId, long authorId, String from, String to, Instant firstPublicAt)
        implements DomainEvent {
}
