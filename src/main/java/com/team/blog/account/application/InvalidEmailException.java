package com.team.blog.account.application;

/** 이메일 형식 위반(일반 형식, 최대 254자 — FR-005). */
public class InvalidEmailException extends RuntimeException {

    public static final String CODE = "EMAIL_INVALID";

    public InvalidEmailException() {
        super(CODE);
    }
}
