package com.team.blog.post.application;

import java.time.Instant;

/** 발행 성공 응답(05 §5). 같은 요청 식별자의 재요청에는 이 값을 그대로 돌려준다. */
public record PublishResult(String url, Instant publishedAt, Instant firstPublicAt, Instant editedAt, long version) {
}
