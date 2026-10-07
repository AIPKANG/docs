package com.team.blog.account.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 001 통합 테스트 공용 요청 모양. */
final class AuthTestSupport {

    static final String PASSWORD = "Blog#2026ok";

    private AuthTestSupport() {
    }

    static MockHttpServletRequestBuilder signup(String email, String handle, String nickname) {
        return signup(email, handle, PASSWORD, PASSWORD, nickname, true, true);
    }

    static MockHttpServletRequestBuilder signup(String email, String handle, String password, String confirm,
                                                String nickname, boolean terms, boolean privacy) {
        MockHttpServletRequestBuilder builder = post("/signup").with(csrf())
                .param("email", email)
                .param("handle", handle)
                .param("password", password)
                .param("passwordConfirm", confirm)
                .param("nickname", nickname);
        if (terms) {
            builder.param("agreeTerms", "true");
        }
        if (privacy) {
            builder.param("agreePrivacy", "true");
        }
        return builder;
    }

    static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/login").with(csrf()).param("email", email).param("password", password);
    }
}
