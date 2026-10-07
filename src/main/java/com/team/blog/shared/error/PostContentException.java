package com.team.blog.shared.error;

/** 글 저장 입력 규칙 위반 → 400 ({@code TITLE_TOO_LONG}, {@code CONTENT_TOO_LONG}, {@code INVALID_REQUEST}). */
public class PostContentException extends RuntimeException {

    private final String code;

    public PostContentException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
