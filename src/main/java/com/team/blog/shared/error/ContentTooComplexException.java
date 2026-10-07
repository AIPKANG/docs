package com.team.blog.shared.error;

/** 본문 중첩 20단계 초과 또는 렌더링 1초 초과 → 400 {@code CONTENT_TOO_COMPLEX}(12 §7-5). */
public class ContentTooComplexException extends RuntimeException {

    public static final String CODE = "CONTENT_TOO_COMPLEX";

    public ContentTooComplexException() {
        super(CODE);
    }
}
