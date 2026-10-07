package com.team.blog.shared.error;

/** 소셜 로그인 계정은 비밀번호가 없다 → 400 {@code PASSWORD_NOT_SUPPORTED}(11 §6-2). */
public class PasswordNotSupportedException extends RuntimeException {

    public static final String CODE = "PASSWORD_NOT_SUPPORTED";

    public PasswordNotSupportedException() {
        super(CODE);
    }
}
