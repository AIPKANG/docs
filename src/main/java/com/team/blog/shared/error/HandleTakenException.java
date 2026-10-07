package com.team.blog.shared.error;

/** 가입 제출 경합에서 진 쪽 → 409 {@code HANDLE_TAKEN_CONCURRENTLY} + 대안 주소. */
public class HandleTakenException extends RuntimeException {

    public static final String CODE = "HANDLE_TAKEN_CONCURRENTLY";

    private final String suggestion;

    public HandleTakenException(String suggestion) {
        super(CODE);
        this.suggestion = suggestion;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
