package com.team.blog.shared.error;

/** 하루 업로드 장수(200) 초과 → 429 {@code DAILY_UPLOAD_LIMIT} + {@code Retry-After}(한국 시간 자정까지, 23 §3). */
public class DailyUploadLimitException extends RuntimeException {

    public static final String CODE = "DAILY_UPLOAD_LIMIT";

    private final long retryAfterSeconds;

    public DailyUploadLimitException(long retryAfterSeconds) {
        super(CODE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
