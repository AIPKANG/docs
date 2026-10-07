package com.team.blog.account.application;

/** 필수 약관(이용약관·개인정보) 중 하나라도 동의하지 않음(FR-006). 계정을 만들기 전에 거부한다. */
public class AgreementRequiredException extends RuntimeException {

    public static final String CODE = "AGREEMENT_REQUIRED";

    public AgreementRequiredException() {
        super(CODE);
    }
}
