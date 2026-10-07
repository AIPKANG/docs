package com.team.blog.shared.error;

/** 요청 본문 크기 초과 → 413 {@code PAYLOAD_TOO_LARGE}(004 FR-008). */
public class PayloadTooLargeException extends RuntimeException {

    public static final String CODE = "PAYLOAD_TOO_LARGE";

    public PayloadTooLargeException() {
        super(CODE);
    }
}
