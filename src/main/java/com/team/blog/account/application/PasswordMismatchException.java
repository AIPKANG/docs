package com.team.blog.account.application;

/** 비밀번호와 비밀번호 확인이 다름. */
public class PasswordMismatchException extends RuntimeException {

    public static final String CODE = "PASSWORD_MISMATCH";

    public PasswordMismatchException() {
        super(CODE);
    }
}
