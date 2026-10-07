package com.team.blog.account.application;

/** 이미 이메일 가입된 주소(FR-007): "이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]". */
public class DuplicateEmailException extends RuntimeException {

    public static final String CODE = "EMAIL_DUPLICATE";

    public DuplicateEmailException() {
        super(CODE);
    }
}
