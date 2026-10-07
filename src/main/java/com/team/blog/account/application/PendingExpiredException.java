package com.team.blog.account.application;

/** 소셜 가입 대기 정보가 없거나 10분이 지남 → 다시 소셜 로그인부터(FR-021). */
public class PendingExpiredException extends RuntimeException {

    public PendingExpiredException() {
        super("PENDING_SOCIAL_SIGNUP_EXPIRED");
    }
}
