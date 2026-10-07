package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 007 T416·SC-005: 편집·미리보기·오류 응답에도 보안 헤더 세 가지. */
class SecurityHeadersPostIT extends IntegrationTestBase {

    @Test
    void securityHeadersOnEditorPreviewAndErrors() throws Exception {
        long me = writer(members, "headers");
        long postId = posts.draft(me, "", "", 0);
        MockHttpServletRequestBuilder[] requests = {
            get("/write/{id}", postId).with(TestAuth.member(me)),
            post("/api/markdown/preview").with(csrf()).with(TestAuth.member(me)).contentType(MediaType.APPLICATION_JSON).content("{\"contentMd\":\"x\"}"),
            get("/write/{id}", 999999).with(TestAuth.member(me)),
            get("/manage/posts")
        };
        for (MockHttpServletRequestBuilder request : requests) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
            String csp = response.getHeader("Content-Security-Policy");
            assertThat(csp).contains("script-src 'self'").contains("object-src 'none'").contains("frame-ancestors 'none'")
                    .contains("base-uri 'none'").contains("form-action 'self'").doesNotContain("unsafe-eval");
            assertThat(csp.substring(csp.indexOf("script-src"), csp.indexOf(';', csp.indexOf("script-src"))))
                    .doesNotContain("unsafe-inline");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        }
    }
}
