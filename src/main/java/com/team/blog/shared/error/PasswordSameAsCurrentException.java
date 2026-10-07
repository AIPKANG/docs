package com.team.blog.shared.error;

/** 새 비밀번호가 현재와 같음 → 400 {@code PASSWORD_SAME_AS_CURRENT}. */
public class PasswordSameAsCurrentException extends RuntimeException {

    public static final String CODE = "PASSWORD_SAME_AS_CURRENT";

    public PasswordSameAsCurrentException() {
        super(CODE);
    }
}
