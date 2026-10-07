package com.team.blog.shared.error;

/** 현재 비밀번호를 5번 연속 틀려 15분 잠금 → 429 {@code PASSWORD_CHANGE_LOCKED} + {@code Retry-After}(003 research R-13). */
public class PasswordChangeLockedException extends RuntimeException {

    public static final String CODE = "PASSWORD_CHANGE_LOCKED";

    private final long retryAfterSeconds;

    public PasswordChangeLockedException(long retryAfterSeconds) {
        super(CODE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
