package com.team.blog.tag.domain;

/** 태그 하나가 규칙에 맞지 않음: {@code INVALID_TAG}, {@code TAG_TOO_LONG}, {@code TAG_BANNED_WORD}(22 §2). */
public class TagRejectedException extends RuntimeException {

    private final String code;

    public TagRejectedException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
