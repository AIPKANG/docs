package com.team.blog.shared.error;

/** 같은 요청 식별자로 다른 내용 → 422 {@code IDEMPOTENCY_KEY_REUSED}(05 §6). */
public class IdempotencyKeyReusedException extends RuntimeException {

    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";

    public IdempotencyKeyReusedException() {
        super(CODE);
    }
}
