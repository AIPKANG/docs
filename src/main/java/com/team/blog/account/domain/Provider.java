package com.team.blog.account.domain;

/** 로그인 수단. {@code auth_identity.provider}의 {@code ck_auth_provider} 값과 같다. */
public enum Provider {
    LOCAL,
    GOOGLE,
    GITHUB
}
