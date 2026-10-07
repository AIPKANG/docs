package com.team.blog.shared.error;

/** 요청 제한 초과 → 429 {@code RATE_LIMITED} + {@code Retry-After}(초) (research R-14). */
public class RateLimitedException extends RuntimeException {

    public static final String CODE = "RATE_LIMITED";

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(CODE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
