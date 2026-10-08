package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.shared.event.PostVisibilityChanged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 006 T606: 다시 발행 없이 공개 범위 바꾸기(US2, FR-014~FR-021). */
@RecordApplicationEvents
class VisibilityChangeIT extends IntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    private static MockHttpServletRequestBuilder change(long postId, String to) {
        return patch("/api/posts/{id}/visibility", postId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(to == null ? "{}" : "{\"visibility\":\"" + to + "\"}");
    }

    @Test
    void changesImmediatelyWithoutTouchingEditsAndKeepsFirstPublicAt() throws Exception {
        clock.set(T0);
        long me = writer(members, "visowner");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, "제목", "본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        jdbc.update("UPDATE post SET edited_at = '2026-10-01T01:00:00Z', comment_count = 3, like_count = 5 WHERE id = ?", postId);
        posts.workingCopy(postId, "작업본", "", 2);
        Map<String, Object> before = posts.post(postId);

        clock.advance(Duration.ofDays(1));
        mockMvc.perform(change(postId, "PRIVATE").with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.firstPublicAt").value("2026-10-01T00:00:00Z"));
        mockMvc.perform(get("/@visowner/posts/{id}", postId)).andExpect(status().isNotFound());
        assertThat(events.stream(PostVisibilityChanged.class))
                .anyMatch(e -> e.postId() == postId && e.from().equals("PUBLIC") && e.to().equals("PRIVATE"));

        clock.advance(Duration.ofDays(1));
        mockMvc.perform(change(postId, "PUBLIC").with(TestAuth.member(me))).andExpect(status().isOk());
        Map<String, Object> after = posts.post(postId);
        for (String kept : new String[] {"edited_at", "edit_version", "first_public_at", "published_at", "comment_count", "like_count"}) {
            assertThat(after.get(kept)).as(kept).isEqualTo(before.get(kept));
        }
        assertThat(posts.workingCopyRow(postId)).containsEntry("title", "작업본");
        mockMvc.perform(get("/@visowner/posts/{id}", postId)).andExpect(status().isOk());
    }

    @Test
    void firstPublicTimeIsSetWhenPrivatePostBecomesPublicFirstTime() throws Exception {
        clock.set(T0);
        long me = writer(members, "vislate");
        long postId = posts.published(me, "비공개 발행", "본문", 1, T0);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', first_public_at = NULL WHERE id = ?", postId);
        clock.advance(Duration.ofDays(3));
        mockMvc.perform(change(postId, "PUBLIC").with(TestAuth.member(me)))
                .andExpect(jsonPath("$.firstPublicAt").value("2026-10-04T00:00:00Z"));
        assertThat(((Timestamp) posts.post(postId).get("first_public_at")).toInstant()).isEqualTo(T0.plus(Duration.ofDays(3)));
    }

    @Test
    void draftStoresValueOnlyAndSameValueIsNoop() throws Exception {
        long me = writer(members, "visdraft");
        long draft = posts.draft(me, "임시", "", 0);
        mockMvc.perform(change(draft, "PRIVATE").with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.post(draft)).containsEntry("visibility", "PRIVATE").containsEntry("status", "DRAFT");
        assertThat(posts.post(draft).get("first_public_at")).isNull();
        mockMvc.perform(change(draft, "PUBLIC").with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.post(draft).get("first_public_at")).isNull();
        Object updated = posts.post(draft).get("updated_at");
        mockMvc.perform(change(draft, "PUBLIC").with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.post(draft).get("updated_at")).isEqualTo(updated);
    }

    @Test
    void permissionsAndInvalidValues() throws Exception {
        long owner = writer(members, "visperm");
        long other = writer(members, "visintr");
        long postId = posts.published(owner, "글", "본문", 1, T0);
        long trashed = posts.published(other, "휴지통", "본문", 1, T0);
        posts.trash(trashed);
        mockMvc.perform(change(postId, "PRIVATE").with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(change(postId, "PRIVATE").with(TestAuth.admin(other))).andExpect(status().isNotFound());
        mockMvc.perform(change(trashed, "PRIVATE").with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(change(987654, "PRIVATE").with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(change(postId, "FRIENDS").with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(change(postId, "PRIVATE")).andExpect(status().isUnauthorized());
        long unverified = members.localMember("visunver", "미인증자", "visunver@example.com", "Blog#2026ok", false);
        mockMvc.perform(change(postId, "PRIVATE").with(TestAuth.member(unverified))).andExpect(status().isForbidden());
        for (String bad : new String[] {"PROTECTED", "public", null}) { // FRIENDS는 025(강성찬 개인 확장)로 허용
            mockMvc.perform(change(postId, bad).with(TestAuth.member(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("INVALID_VISIBILITY"));
        }
        assertThat(posts.post(postId)).containsEntry("visibility", "PUBLIC");
    }

    @Test
    void concurrentChangesKeepFirstPublicRule() throws Exception {
        clock.set(T0);
        long me = writer(members, "visrace");
        long postId = posts.published(me, "글", "본문", 1, T0);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', first_public_at = NULL WHERE id = ?", postId);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String to = i % 2 == 0 ? "PUBLIC" : "PRIVATE";
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(change(postId, to).with(TestAuth.member(me))).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(((Timestamp) posts.post(postId).get("first_public_at")).toInstant()).isEqualTo(T0);
    }
}
