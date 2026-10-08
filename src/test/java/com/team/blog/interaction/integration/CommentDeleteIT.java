package com.team.blog.interaction.integration;

import static com.team.blog.interaction.integration.CommentTestSupport.create;
import static com.team.blog.interaction.integration.CommentTestSupport.remove;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** 014: 삭제·자리·자리 정리·댓글 수·권한·동시 삭제와 답글·글 상태(FR-024~FR-029, FR-032). */
class CommentDeleteIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com", "Blog#2026ok", true);
    }

    private long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long comment(long post, long who, String content, Long replyTo) throws Exception {
        return id(mockMvc.perform(create(post, content, replyTo).with(TestAuth.member(who)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private int count(long post) {
        return (Integer) posts.post(post).get("comment_count");
    }

    private boolean exists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ?", Integer.class, id) == 1;
    }

    @Test
    void placeholderAndCleanupAndCounts() throws Exception {
        long author = member("delauthor");
        long a = member("dela");
        long b = member("delb");
        long post = posts.published(author, "글", "본문", 1, T);
        long root = comment(post, a, "최상위", null);
        long reply = comment(post, b, "답글", root);
        long lonely = comment(post, a, "답글 없는 최상위", null);
        assertThat(count(post)).isEqualTo(3);

        mockMvc.perform(remove(root).with(TestAuth.member(a))).andExpect(status().isNoContent());
        assertThat(exists(root)).isTrue();
        assertThat(jdbc.queryForObject("SELECT content FROM comment WHERE id = ?", String.class, root)).isEmpty();
        assertThat(count(post)).isEqualTo(2);
        mockMvc.perform(get("/api/posts/{id}/comments", post)).andExpect(jsonPath("$.items[0].state").value("DELETED"));

        mockMvc.perform(remove(reply).with(TestAuth.member(b))).andExpect(status().isNoContent());
        assertThat(exists(reply)).isFalse();
        assertThat(exists(root)).isFalse(); // 자리 정리
        assertThat(count(post)).isEqualTo(1);

        mockMvc.perform(remove(lonely).with(TestAuth.member(a))).andExpect(status().isNoContent());
        assertThat(exists(lonely)).isFalse();
        assertThat(count(post)).isZero();
    }

    @Test
    void onlyAuthorDeletesHiddenAndUnverifiedAllowed() throws Exception {
        long author = member("delperm");
        long a = member("delperma");
        long admin = member("delpermadm");
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", admin);
        long post = posts.published(author, "글", "본문", 1, T);
        long c = comment(post, a, "내 댓글", null);
        mockMvc.perform(remove(c).with(TestAuth.member(author))).andExpect(status().isNotFound());
        mockMvc.perform(remove(c).with(TestAuth.admin(admin))).andExpect(status().isNotFound());
        mockMvc.perform(remove(c)).andExpect(status().isUnauthorized());
        assertThat(exists(c)).isTrue();
        // 숨긴 내 댓글 삭제는 허용, 댓글 수는 숨김 때 이미 빠짐
        jdbc.update("UPDATE comment SET hidden_at = now() WHERE id = ?", c);
        jdbc.update("UPDATE post SET comment_count = 0 WHERE id = ?", post);
        mockMvc.perform(remove(c).with(TestAuth.member(a))).andExpect(status().isNoContent());
        assertThat(count(post)).isZero();
        // 인증 전 회원의 자기 댓글 삭제
        long unverified = members.localMember("delunver", "미인증", "delunver@example.com", "Blog#2026ok", false);
        long own = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '인증 전') RETURNING id",
                Long.class, post, unverified);
        mockMvc.perform(remove(own).with(TestAuth.member(unverified))).andExpect(status().isNoContent());
    }

    @Test
    void concurrentRootDeleteAndReplyAreConsistent() throws Exception {
        long author = member("delrace");
        long a = member("delracea");
        long b = member("delraceb");
        for (int round = 0; round < 5; round++) {
            long post = posts.published(author, "글 " + round, "본문", 1, T);
            long root = comment(post, a, "최상위 " + round, null);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> f = new ArrayList<>();
            f.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(remove(root).with(TestAuth.member(a))).andReturn().getResponse().getStatus();
            }));
            f.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(create(post, "답글 " + System.nanoTime(), root).with(TestAuth.member(b)))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            int del = f.get(0).get();
            int rep = f.get(1).get();
            pool.shutdown();
            assertThat(del).isEqualTo(204);
            if (rep == 201) {
                // 답글이 먼저: 최상위는 자리로 남는다
                assertThat(exists(root)).isTrue();
                assertThat(count(post)).isEqualTo(1);
            } else {
                assertThat(rep).isEqualTo(400);
                assertThat(exists(root)).isFalse();
                assertThat(count(post)).isZero();
            }
        }
    }

    @Test
    void commentsFollowPostVisibility() throws Exception {
        long author = member("delvis");
        long a = member("delvisa");
        long post = posts.published(author, "글", "본문", 1, T);
        comment(post, a, "남의 댓글", null);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", post);
        mockMvc.perform(get("/api/posts/{id}/comments", post).with(TestAuth.member(a))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{id}/comments", post).with(TestAuth.member(author)))
                .andExpect(jsonPath("$.items.length()").value(1));
        jdbc.update("UPDATE post SET visibility = 'PUBLIC' WHERE id = ?", post);
        mockMvc.perform(get("/api/posts/{id}/comments", post)).andExpect(jsonPath("$.items.length()").value(1));
        posts.trash(post);
        mockMvc.perform(get("/api/posts/{id}/comments", post).with(TestAuth.member(author))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE post_id = ?", Integer.class, post)).isEqualTo(1);
    }
}
