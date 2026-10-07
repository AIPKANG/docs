package com.team.blog.account.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 소셜 로그인 성공 직후 재현(SocialLoginProbeController)·마무리 제출 요청 모양. */
final class SocialTestSupport {

    private SocialTestSupport() {
    }

    static MockHttpServletRequestBuilder socialLogin(String provider, String id, String verifiedEmail, String name) {
        MockHttpServletRequestBuilder builder = post("/test/social-login").with(csrf())
                .param("provider", provider).param("id", id).param("picture", "https://example.com/p.png");
        if (verifiedEmail != null) {
            builder.param("email", verifiedEmail);
        }
        if (name != null) {
            builder.param("name", name);
        }
        return builder;
    }

    static MockHttpServletRequestBuilder completeSocial(String handleBody, String nickname) {
        return post("/signup/social").with(csrf())
                .param("handle", handleBody).param("nickname", nickname)
                .param("agreeTerms", "true").param("agreePrivacy", "true").param("useSocialPicture", "true");
    }
}
