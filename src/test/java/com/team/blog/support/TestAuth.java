package com.team.blog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 테스트용 로그인 도우미. principal 이름 = memberId 문자열(001 research R-3) — 001 로그인 구현 전에도
 * "로그인한 본인" 시나리오를 시험한다.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor member(long memberId) {
        return user(String.valueOf(memberId)).roles("USER");
    }

    public static RequestPostProcessor admin(long memberId) {
        return user(String.valueOf(memberId)).roles("ADMIN");
    }
}
