package com.team.blog.account.application;

/** 그 이메일의 이메일 가입 계정이 탈퇴 유예 중(13 §3): "탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요". */
public class WithdrawnAccountExistsException extends RuntimeException {

    public static final String CODE = "EMAIL_WITHDRAWN_PENDING";

    public WithdrawnAccountExistsException() {
        super(CODE);
    }
}
