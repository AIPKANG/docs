package com.team.blog.account.application;

/** 같은 IP의 로그인 시도가 1분 한도를 넘음(FR-025). 화면 문구는 잠금과 같다. */
public class IpRateLimitedException extends RuntimeException {

    public IpRateLimitedException() {
        super("LOGIN_IP_RATE_LIMITED");
    }
}
