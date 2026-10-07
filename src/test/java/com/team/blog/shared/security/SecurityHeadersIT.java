package com.team.blog.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class SecurityHeadersIT extends IntegrationTestBase {

    @Test
    void everyResponseCarriesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    /** 001 T174: 인증 화면도 같은 보안 헤더를 갖는다. */
    @Test
    void authPagesCarrySecurityHeaders() throws Exception {
        for (String path : new String[] {"/login", "/signup", "/signup/social", "/password/forgot", "/password/reset"}) {
            mockMvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
        }
    }

    /** 001 T174: 세션 쿠키 속성(FR-028). */
    @Test
    void sessionCookieIsHttpOnlySecureAndLax() throws Exception {
        String setCookie = mockMvc.perform(get("/login")).andReturn().getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSION=")).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(setCookie)
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Max-Age=1209600");
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/anything"))
                .andExpect(status().isForbidden());
    }
}
