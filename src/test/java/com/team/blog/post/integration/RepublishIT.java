package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.publishBody;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.AutosaveFlusher;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 005 T508: 다시 발행(US2, FR-014, FR-015, SC-002, SC-003) + 늦은 공개(US5, SC-007). */
class RepublishIT extends IntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    AutosaveFlusher flusher;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void republishKeepsAddressDatesAndCountersAndShowsEdited() throws Exception {
        clock.set(T0);
        long me = writer(members, "republisher");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, "첫 제목", "첫 본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        jdbc.update("UPDATE post SET view_count = 42, like_count = 7, comment_count = 3 WHERE id = ?", postId);
        Map<String, Object> first = posts.post(postId);

        // 고치는 동안 독자는 발행본을 본다
        clock.advance(Duration.ofDays(2));
        mockMvc.perform(autosave(postId, "고친 제목", "고친 본문", 1).with(TestAuth.member(me))).andExpect(status().isOk());
        flusher.flushBatch(500);
        assertThat(mockMvc.perform(get("/@republisher/posts/{id}", postId)).andReturn().getResponse().getContentAsString())
                .contains("첫 본문").doesNotContain("고친 본문");

        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("고친 제목", "고친 본문", "[]", "PUBLIC", 2))
                        .with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/@republisher/posts/" + postId))
                .andExpect(jsonPath("$.editedAt").value("2026-10-03T00:00:00Z"))
                .andExpect(jsonPath("$.version").value(3));
        Map<String, Object> after = posts.post(postId);
        for (String kept : new String[] {"published_at", "first_public_at", "view_count", "like_count", "comment_count", "id"}) {
            assertThat(after.get(kept)).as(kept).isEqualTo(first.get(kept));
        }
        assertThat(after).containsEntry("title", "고친 제목");
        assertThat(((Timestamp) after.get("edited_at")).toInstant()).isEqualTo(T0.plus(Duration.ofDays(2)));
        assertThat(posts.workingCopyRow(postId)).isNull();
        assertThat(posts.buffer(postId)).isEmpty();
        String html = mockMvc.perform(get("/@republisher/posts/{id}", postId)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("고친 본문").contains("수정됨").contains("10월 3일");
    }

    @Test
    void visibilityCanToggleButFirstPublicAtIsSetOnceWhenFirstPublic() throws Exception {
        clock.set(T0);
        long me = writer(members, "latepublic");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("비공개로", "본문", "[]", "PRIVATE", 0))
                .with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.post(postId).get("first_public_at")).isNull();

        clock.advance(Duration.ofDays(5));
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("공개로", "본문", "[]", "PUBLIC", 1))
                .with(TestAuth.member(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstPublicAt").value("2026-10-06T00:00:00Z"));
        Instant firstPublic = ((Timestamp) posts.post(postId).get("first_public_at")).toInstant();

        clock.advance(Duration.ofDays(1));
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("다시 비공개", "본문", "[]", "PRIVATE", 2))
                .with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(get("/@latepublic/posts/{id}", postId)).andExpect(status().isNotFound());
        clock.advance(Duration.ofDays(1));
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("다시 공개", "본문", "[]", "PUBLIC", 3))
                .with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(((Timestamp) posts.post(postId).get("first_public_at")).toInstant()).isEqualTo(firstPublic);
        mockMvc.perform(get("/@latepublic/posts/{id}", postId)).andExpect(status().isOk());
    }
}
