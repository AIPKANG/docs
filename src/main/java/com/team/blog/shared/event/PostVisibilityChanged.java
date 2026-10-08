package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 공개 범위가 바뀜(006 FR-021). 검색 색인·sitemap·알림이 커밋 후 구독한다. {@code firstPublic}은 이번 변경으로 처음 전체 공개됐을 때
 * (017 새 글 알림은 이때 한 번만).
 */
public record PostVisibilityChanged(long postId, long authorId, String from, String to, Instant firstPublicAt,
                                    boolean firstPublic) implements DomainEvent {
}
