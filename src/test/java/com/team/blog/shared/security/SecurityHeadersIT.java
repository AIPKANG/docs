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

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/anything"))
                .andExpect(status().isForbidden());
    }
}
