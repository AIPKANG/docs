package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.body;
import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 004 T322: 권한 표(42 §5-2, FR-014, SC-008)와 요청 제한·크기(FR-008). */
class AutosavePermissionIT extends IntegrationTestBase {

    private void assertUntouched(long postId, Map<String, Object> before) {
        Map<String, Object> after = posts.post(postId);
        assertThat(after.get("title")).isEqualTo(before.get("title"));
        assertThat(after.get("content_md")).isEqualTo(before.get("content_md"));
        assertThat(after.get("edit_version")).isEqualTo(before.get("edit_version"));
        assertThat(posts.buffer(postId)).isEmpty();
    }

    @Test
    void othersMissingAndTrashedPostsAreIndistinguishable404() throws Exception {
        long owner = writer(members, "ownerperm");
        long me = writer(members, "intruder");
        long othersPost = posts.draft(owner, "주인 글", "본문", 4);
        long publishedOthers = posts.published(owner, "발행", "본문", 2, Instant.parse("2026-10-01T00:00:00Z"));
        long myTrashed = posts.draft(me, "내 휴지통", "", 0);
        posts.trash(myTrashed);
        Map<String, Object> before = posts.post(othersPost);

        String notFound = null;
        for (long target : new long[] {othersPost, publishedOthers, myTrashed, 987654}) {
            for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                    autosave(target, "침입", "x", 4), manualSave(target, "침입", "x", 4),
                    get("/api/posts/{id}/editing", target),
                    delete("/api/posts/{id}/working-copy", target).with(csrf())}) {
                posts.resetRateLimit(me);
                String body = mockMvc.perform(request.with(TestAuth.member(me)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                        .andReturn().getResponse().getContentAsString();
                if (notFound == null) {
                    notFound = body;
                }
                assertThat(body).isEqualTo(notFound);
            }
        }
        // 관리자도 남의 글은 404
        mockMvc.perform(autosave(othersPost, "관리자", "x", 4).with(TestAuth.admin(me))).andExpect(status().isNotFound());
        assertUntouched(othersPost, before);
        assertThat(posts.workingCopyRow(publishedOthers)).isNull();
    }

    @Test
    void guestGets401AndUnverifiedGets403() throws Exception {
        long owner = writer(members, "ownerauth");
        long postId = posts.draft(owner, "글", "", 0);
        Map<String, Object> before = posts.post(postId);
        mockMvc.perform(autosave(postId, "비회원", "", 0)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        long unverified = members.localMember("unverauth", "미인증", "unverauth@example.com", "Blog#2026ok", false);
        mockMvc.perform(autosave(postId, "미인증", "", 0).with(TestAuth.member(unverified)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mockMvc.perform(manualSave(postId, "미인증", "", 0).with(TestAuth.member(unverified)))
                .andExpect(status().isForbidden());
        assertUntouched(postId, before);
    }

    @Test
    void secondAutosaveWithinFiveSecondsIsRateLimited() throws Exception {
        long me = writer(members, "ratelimit");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(autosave(postId, "1", "", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(autosave(postId, "2", "", 1).with(TestAuth.member(me)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(posts.buffer(postId)).containsEntry("title", "1");
    }

    @Test
    void oversizedBodyIs413AndTooLongFieldsAre400() throws Exception {
        long me = writer(members, "sizelimit");
        long postId = posts.draft(me, "", "", 0);
        String huge = "가".repeat(400_000); // UTF-8 120만 바이트
        mockMvc.perform(autosave(postId, "", huge, 0).with(TestAuth.member(me)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        mockMvc.perform(autosave(postId, "가".repeat(101), "", 0).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TITLE_TOO_LONG"));
        mockMvc.perform(manualSave(postId, "", "a".repeat(100_001), 0).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTENT_TOO_LONG"));
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(csrf()).with(TestAuth.member(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"contentMd\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(csrf()).with(TestAuth.member(me))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", "", -1)))
                .andExpect(status().isBadRequest());
        // 100자 제목·10만 자 본문은 된다(이모지는 1자로 센다)
        mockMvc.perform(manualSave(postId, "😀".repeat(100), "b".repeat(100_000), 0).with(TestAuth.member(me)))
                .andExpect(status().isOk());
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(1L);
    }

    @Test
    void csrfIsRequired() throws Exception {
        long me = writer(members, "csrfcheck");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(TestAuth.member(me))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", "", 0)))
                .andExpect(status().isForbidden());
        assertThat(posts.buffer(postId)).isEmpty();
    }
}
