package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 005 T514: 남의 글은 발행·수정할 수 없고 존재도 드러나지 않는다(US4, FR-021, FR-022, SC-005). */
class PublishPermissionIT extends IntegrationTestBase {

    @Test
    void othersAdminAndTrashedAre404AndPostIsUnchanged() throws Exception {
        long owner = writer(members, "pubowner");
        long other = writer(members, "pubother");
        long draft = posts.draft(owner, "주인 임시글", "본문", 2);
        long published = posts.published(owner, "주인 발행", "본문", 3, Instant.parse("2026-10-01T00:00:00Z"));
        long trashed = posts.draft(other, "내 휴지통", "본문", 0);
        posts.trash(trashed);
        Map<String, Object> draftBefore = posts.post(draft);
        Map<String, Object> publishedBefore = posts.post(published);

        mockMvc.perform(publish(draft, "침입", "x", 2).with(TestAuth.member(other))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(publish(published, "침입", "x", 3).with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(publish(published, "관리자", "x", 3).with(TestAuth.admin(other))).andExpect(status().isNotFound());
        mockMvc.perform(publish(trashed, "휴지통", "x", 0).with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(publish(987654, "없음", "x", 0).with(TestAuth.member(other))).andExpect(status().isNotFound());
        assertThat(posts.post(draft)).isEqualTo(draftBefore);
        assertThat(posts.post(published)).isEqualTo(publishedBefore);
    }

    @Test
    void authorFieldInBodyIsIgnored() throws Exception {
        long owner = writer(members, "realowner");
        long other = writer(members, "fakeowner");
        long othersPost = posts.draft(owner, "주인", "본문", 0);
        mockMvc.perform(post("/api/posts/{id}/publish", othersPost).with(csrf()).with(TestAuth.member(other))
                        .header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"contentMd\":\"c\",\"tags\":[],\"visibility\":\"PUBLIC\",\"baseVersion\":0,\"authorId\":" + owner + "}"))
                .andExpect(status().isNotFound());
        assertThat(posts.post(othersPost)).containsEntry("status", "DRAFT");
    }

    @Test
    void guestAndUnverified() throws Exception {
        long owner = writer(members, "pubauth");
        long postId = posts.draft(owner, "글", "본문", 0);
        mockMvc.perform(publish(postId, "제목", "본문", 0)).andExpect(status().isUnauthorized());
        long unverified = members.localMember("pubunver", "미인증자", "pubunver@example.com", "Blog#2026ok", false);
        long hisPost = posts.draft(unverified, "", "", 0);
        mockMvc.perform(publish(hisPost, "제목", "본문", 0).with(TestAuth.member(unverified)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(posts.post(hisPost)).containsEntry("status", "DRAFT");
    }
}
