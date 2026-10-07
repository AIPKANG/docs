package com.team.blog.account.domain;

/** 회원 동의 종류. {@code ck_member_agreement_type} 값과 같다. 가입은 {@code TERMS}·{@code PRIVACY}(필수 2개). */
public enum AgreementType {
    TERMS,
    PRIVACY,
    AI
}
