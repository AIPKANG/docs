package com.team.blog.account.domain;

/** 블로그 주소 오류 코드(data-model §2.1). */
public enum HandleViolation {
    HANDLE_INVALID_FORMAT,
    HANDLE_PREFIX_MISMATCH,
    HANDLE_RESERVED,
    HANDLE_BANNED_WORD,
    HANDLE_DUPLICATE,
    HANDLE_TAKEN_CONCURRENTLY;

    private static final String PREFIX = "HANDLE_";

    /** 사용 가능 여부 API용 이유: {@code HANDLE_} 접두를 뗀 값(예: {@code DUPLICATE}). */
    public String reason() {
        return name().substring(PREFIX.length());
    }
}
