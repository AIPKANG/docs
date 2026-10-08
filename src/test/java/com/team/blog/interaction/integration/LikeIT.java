package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.interaction.application.LikeReconcileJob;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostUnliked;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** 015: 좋아요(FR-001~FR-023). */
@RecordApplicationEvents
class LikeIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @Autowired
    com.team.blog.post.application.PostCounters postCounters;

    @Autowired
    com.team.blog.post.application.JobLock jobLock;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private int likes(long post) {
        return (Integer) posts.post(post).get("like_count");
    }

    @Test
    void setStateIdempotentWithEventsOnlyOnChange() throws Exception {
        long author = member("likeauthor");
        long a = member("likera");
        long post = posts.published(author, "글", "본문", 1, T);
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(events.stream(PostLiked.class).filter(e -> e.postId() == post)).hasSize(1);
        mockMvc.perform(delete("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0));
        mockMvc.perform(delete("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.likeCount").value(0));
        assertThat(events.stream(PostUnliked.class).filter(e -> e.postId() == post)).hasSize(1);
    }

    @Test
    void concurrentRequestsKeepOneRecordAndExactCount() throws Exception {
        long author = member("likerace");
        long post = posts.published(author, "글", "본문", 1, T);
        List<Long> likers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            likers.add(member("likerace" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (long liker : likers) {
            for (int k = 0; k < 2; k++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(liker)))
                            .andReturn().getResponse().getStatus();
                }));
            }
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(likes(post)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_like WHERE post_id = ?", Integer.class, post)).isEqualTo(10);
    }

    @Test
    void permissionsOrderAndRules() throws Exception {
        long author = member("likeperm");
        long a = member("likeperma");
        long unverified = members.localMember("likeunver", "미인증", "likeunver@example.com", "Blog#2026ok", false);
        long post = posts.published(author, "글", "본문", 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long draft = posts.draft(author, "임시", "", 0);
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/posts/{id}/like", priv).with(csrf()).with(TestAuth.member(unverified)))
                .andExpect(status().isForbidden()); // 계정 상태가 먼저
        for (long hidden : new long[] {priv, draft, 999999}) {
            mockMvc.perform(put("/api/posts/{id}/like", hidden).with(csrf()).with(TestAuth.member(a))).andExpect(status().isNotFound());
        }
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(author)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_LIKE_OWN_POST"));
        assertThat(likes(post)).isZero();
        for (int i = 0; i < 59; i++) {
            mockMvc.perform((i % 2 == 0 ? put("/api/posts/{id}/like", post) : delete("/api/posts/{id}/like", post))
                    .with(csrf()).with(TestAuth.member(a))).andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a))).andExpect(status().isOk());
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void likesSurviveVisibilityAndTrashAndReconcileFixesDrift() throws Exception {
        long author = member("likekeep");
        long a = member("likekeepa");
        long post = posts.published(author, "글", "본문", 1, T);
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a))).andExpect(status().isOk());
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", post);
        mockMvc.perform(put("/api/posts/{id}/like", post).with(csrf()).with(TestAuth.member(a))).andExpect(status().isNotFound());
        jdbc.update("UPDATE post SET visibility = 'PUBLIC' WHERE id = ?", post);
        posts.trash(post);
        jdbc.update("UPDATE post SET deleted_at = NULL WHERE id = ?", post);
        assertThat(likes(post)).isEqualTo(1);
        jdbc.update("UPDATE post SET like_count = 42 WHERE id = ?", post);
        LikeReconcileJob reconcileJob = new LikeReconcileJob(postCounters, jobLock);
        assertThat(reconcileJob.runOnce()).isEqualTo(1);
        assertThat(likes(post)).isEqualTo(1);
        assertThat(reconcileJob.runOnce()).isZero();
    }

    @Test
    void detailShowsMyStateAndFormWorksWithoutScript() throws Exception {
        long author = member("likeview");
        long a = member("likeviewa");
        long post = posts.published(author, "글", "본문", 1, T);
        String before = mockMvc.perform(get("/@likeview/posts/{id}", post).with(TestAuth.member(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("aria-pressed=\"false\"").contains("aria-label=\"좋아요 (0)\"").contains("♡");
        mockMvc.perform(post("/@likeview/posts/{id}/like", post).param("liked", "true").with(csrf()).with(TestAuth.member(a)))
                .andExpect(status().isSeeOther());
        String after = mockMvc.perform(get("/@likeview/posts/{id}", post).with(TestAuth.member(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(after).contains("aria-pressed=\"true\"").contains("좋아요 취소 (1)").contains("♥");
        String mine = mockMvc.perform(get("/@likeview/posts/{id}", post).with(TestAuth.member(author)))
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).doesNotContain("class=\"like-button\"").contains("♥ 1");
        assertThat(mockMvc.perform(get("/@likeview/posts/{id}", post)).andReturn().getResponse().getContentAsString())
                .contains("data-guest=\"true\"").contains("class=\"like-button\"");
    }
}
