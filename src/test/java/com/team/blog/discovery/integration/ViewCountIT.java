package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.discovery.application.ViewFlusher;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import jakarta.servlet.http.Cookie;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 016: 조회수(FR-001~FR-019). */
class ViewCountIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ViewFlusher flusher;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private MockHttpServletRequestBuilder view(long postId) {
        return view(postId, "Mozilla/5.0");
    }

    private MockHttpServletRequestBuilder view(long postId, String userAgent) {
        return post("/api/posts/{id}/views", postId).with(csrf()).header("User-Agent", userAgent);
    }

    private long views(long postId) {
        flusher.flush();
        return (Long) posts.post(postId).get("view_count");
    }

    @Test
    void sameVisitorCountsOncePerWindowAndOthersCount() throws Exception {
        clock.set(NOW);
        long author = member("viewauthor");
        long a = member("viewera");
        long b = member("viewerb");
        long post = posts.published(author, "글", "본문", 1, NOW);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(view(post).with(TestAuth.member(a))).andExpect(status().isNoContent());
        }
        mockMvc.perform(view(post).with(TestAuth.member(b))).andExpect(status().isNoContent());
        mockMvc.perform(view(post).cookie(new Cookie("vid", "11111111-1111-1111-1111-111111111111"))).andExpect(status().isNoContent());
        mockMvc.perform(view(post).cookie(new Cookie("vid", "11111111-1111-1111-1111-111111111111"))).andExpect(status().isNoContent());
        mockMvc.perform(view(post).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })).andExpect(status().isNoContent());
        mockMvc.perform(view(post).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })).andExpect(status().isNoContent());
        // 027 강성찬 개인 확장: 30분에 5번까지 → a 3 + b 1 + 쿠키 방문자 2 + IP 방문자 2
        assertThat(views(post)).isEqualTo(8);
        // 기간이 지나면(판정 기록 만료) 다시 센다
        Set<String> seen = redis.keys("view:seen:" + post + ":*");
        assertThat(String.join(",", seen)).doesNotContain("203.0.113.7").doesNotContain("11111111-1111");
        seen.forEach(k -> assertThat(redis.getExpire(k)).isBetween(1L, 1800L));
        redis.delete("view:seen:" + post + ":m:" + a);
        mockMvc.perform(view(post).with(TestAuth.member(a))).andExpect(status().isNoContent());
        assertThat(views(post)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT views FROM post_view_daily WHERE post_id = ? AND view_date = ?",
                Integer.class, post, Date.valueOf(LocalDate.of(2026, 10, 8)))).isEqualTo(9);
    }

    @Test
    void exclusionsAndUnreadablePosts() throws Exception {
        clock.set(NOW);
        long author = member("viewexcl");
        long a = member("viewexcla");
        long admin = member("viewadmin");
        long post = posts.published(author, "글", "본문", 1, NOW);
        mockMvc.perform(view(post).with(TestAuth.member(author))).andExpect(status().isNoContent());
        mockMvc.perform(view(post).with(TestAuth.admin(admin))).andExpect(status().isNoContent());
        mockMvc.perform(view(post, "Slackbot-LinkExpanding 1.0")).andExpect(status().isNoContent());
        mockMvc.perform(view(post, "Mozilla HeadlessChrome/120")).andExpect(status().isNoContent());
        mockMvc.perform(view(post).header("Sec-Purpose", "prefetch").with(TestAuth.member(a))).andExpect(status().isNoContent());
        assertThat(views(post)).isZero();
        long priv = posts.published(author, "비공개", "본문", 1, NOW);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long draft = posts.draft(author, "임시", "", 0);
        for (long hidden : new long[] {priv, draft, 999999}) {
            mockMvc.perform(view(hidden).with(TestAuth.member(a))).andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentSameVisitorCountsOnce() throws Exception {
        clock.set(NOW);
        long author = member("viewrace");
        long a = member("viewracea");
        long post = posts.published(author, "글", "본문", 1, NOW);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(view(post).with(TestAuth.member(a))).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(204);
        }
        pool.shutdown();
        assertThat(views(post)).isEqualTo(5); // 027: 30분에 5번까지
    }

    @Test
    void flushKeepsUpdatedAtRecoversProcessingKeysAndPurgesOldDaily() throws Exception {
        clock.set(NOW);
        long author = member("viewflush");
        long post = posts.published(author, "글", "본문", 1, NOW);
        jdbc.update("UPDATE post SET updated_at = '2026-01-01T00:00:00Z' WHERE id = ?", post);
        // 지난번 반영이 RENAME 뒤 죽은 상황
        redis.opsForHash().put("view:processing:20261007:123", String.valueOf(post), "3");
        redis.opsForHash().put("view:pending:20261008", String.valueOf(post), "2");
        flusher.flush();
        assertThat(posts.post(post).get("view_count")).isEqualTo(5L);
        assertThat(((Timestamp) posts.post(post).get("updated_at")).toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_view_daily WHERE post_id = ?", Integer.class, post)).isEqualTo(2);
        assertThat(redis.keys("view:processing:*")).isEmpty();
        flusher.flush();
        assertThat(posts.post(post).get("view_count")).isEqualTo(5L);
        jdbc.update("INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, 9)", post,
                Date.valueOf(LocalDate.of(2026, 7, 1)));
        assertThat(flusher.purgeOldDaily(Duration.ofDays(90))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_view_daily WHERE post_id = ?", Integer.class, post)).isEqualTo(2);
    }

    @Test
    void detailIssuesVisitorCookieAndShowsNotice() throws Exception {
        long author = member("viewpage");
        long post = posts.published(author, "글", "본문", 1, NOW);
        var guest = mockMvc.perform(get("/@viewpage/posts/{id}", post)).andReturn().getResponse();
        assertThat(guest.getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("vid=") && c.contains("HttpOnly")
                && c.contains("SameSite=Lax") && c.contains("Max-Age=31536000"));
        assertThat(guest.getContentAsString()).contains("같은 사람은 30분에 5번까지 세요").contains("/js/post/post-view.js");
        assertThat(mockMvc.perform(get("/@viewpage/posts/{id}", post).with(TestAuth.member(author)))
                .andReturn().getResponse().getContentAsString()).doesNotContain("/js/post/post-view.js");
        mockMvc.perform(get("/@viewpage/posts/{id}", post)).andExpect(status().isOk());
        assertThat(posts.post(post).get("view_count")).isEqualTo(0L);
    }

    @Test
    void visitorOver60PerMinuteGets429() throws Exception {
        clock.set(NOW);
        long author = member("viewlimit");
        long a = member("viewlimita");
        long post = posts.published(author, "글", "본문", 1, NOW);
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(view(post).with(TestAuth.member(a))).andExpect(status().isNoContent());
        }
        mockMvc.perform(view(post).with(TestAuth.member(a))).andExpect(status().isTooManyRequests());
        mockMvc.perform(view(post).with(TestAuth.member(author))).andExpect(status().isNoContent());
    }
}
