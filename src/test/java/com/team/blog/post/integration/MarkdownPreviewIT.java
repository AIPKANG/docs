package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.json;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.markdown.ContentRenderer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 007 T408: 미리보기 API(FR-020, SC-003). */
class MarkdownPreviewIT extends IntegrationTestBase {

    @Autowired
    ContentRenderer renderer;

    @Autowired
    JsonMapper jsonMapper;

    private static MockHttpServletRequestBuilder preview(String md) {
        return post("/api/markdown/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentMd\":" + json(md) + "}");
    }

    @Test
    void previewEqualsRendererOutput() throws Exception {
        long me = writer(members, "previewer");
        String md = "# 원인\n\n<script>alert(1)</script> [x](javascript:alert(1)) **굵게**\n\n- [x] 완료";
        String body = mockMvc.perform(preview(md).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jsonMapper.readTree(body).get("html").stringValue()).isEqualTo(renderer.render(md).html());
        assertThat(body).doesNotContain("<script").doesNotContain("href=\\\"javascript");
    }

    @Test
    void loginRequiredButUnverifiedMemberMayPreview() throws Exception {
        mockMvc.perform(preview("x")).andExpect(status().isUnauthorized());
        long unverified = members.localMember("previewunv", "미리보기", "previewunv@example.com", "Blog#2026ok", false);
        mockMvc.perform(preview("x").with(TestAuth.member(unverified))).andExpect(status().isOk());
    }

    @Test
    void sixtyPerMinute() throws Exception {
        long me = writer(members, "previewlim");
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(preview("글 " + i).with(TestAuth.member(me))).andExpect(status().isOk());
        }
        mockMvc.perform(preview("61번째").with(TestAuth.member(me)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void tooLongAndTooComplexAreRejected() throws Exception {
        long me = writer(members, "previewbig");
        mockMvc.perform(preview("a".repeat(100_001)).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTENT_TOO_LONG"));
        mockMvc.perform(preview(">".repeat(25) + " x").with(TestAuth.member(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTENT_TOO_COMPLEX"))
                .andExpect(jsonPath("$.message").value("글 구조가 너무 복잡해요 (목록·인용은 20단계까지)"));
    }
}
