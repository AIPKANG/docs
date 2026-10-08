package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.discovery.application.TrendingService;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 019 트렌딩. */
class TrendingIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    TrendingService trending;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private long post(long author, String title, Duration age, int likes, long views) {
        long id = posts.published(author, title, "본문", 1, NOW.minus(age));
        jdbc.update("UPDATE post SET like_count = ?, view_count = ? WHERE id = ?", likes, views, id);
        return id;
    }

    private void comment(long post, long author) {
        jdbc.update("INSERT INTO comment (post_id, author_id, content, created_at, updated_at) VALUES (?, ?, '댓글', ?, ?)",
                post, author, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    @Test
    void rankingFollowsScoreWindowMinimumAndAuthorCap() {
        clock.set(NOW);
        long a = member("trauthor");
        long b = member("trother");
        long c = member("trthird");
        long fresh = post(a, "새 글", Duration.ofHours(1), 1, 0);
        long old = post(b, "하루 된 글", Duration.ofDays(1), 3, 0);
        long outside = post(b, "8일 된 글", Duration.ofDays(8), 100, 0);
        long viewsOnly = post(b, "조회만", Duration.ofHours(1), 0, 10_000);
        long selfComments = post(c, "자기 댓글만", Duration.ofHours(1), 0, 0);
        comment(selfComments, c);
        comment(selfComments, c);
        long commented = post(c, "남의 댓글", Duration.ofHours(2), 0, 0);
        comment(commented, a);
        comment(commented, a);
        long hiddenComment = post(c, "숨긴 댓글만", Duration.ofHours(2), 0, 0);
        jdbc.update("INSERT INTO comment (post_id, author_id, content, hidden_at) VALUES (?, ?, '숨김', now())", hiddenComment, a);
        long priv = post(b, "비공개", Duration.ofHours(1), 50, 0);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long hidden = post(b, "숨김", Duration.ofHours(1), 50, 0);
        jdbc.update("UPDATE post SET hidden_at = now() WHERE id = ?", hidden);
        long prolific = member("trmany");
        List<Long> many = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            many.add(post(prolific, "다작" + i, Duration.ofHours(3 + i), 5, 0));
        }
        List<Long> ranked = trending.rank(100);
        assertThat(ranked).doesNotContain(outside, viewsOnly, selfComments, hiddenComment, priv, hidden);
        assertThat(ranked).contains(fresh, old, commented);
        // 같은 반응이면 최근 글이 높다
        assertThat(ranked.indexOf(fresh)).isLessThan(ranked.indexOf(old));
        // 작성자당 최대 3개(점수 상위 3개)
        assertThat(ranked.stream().filter(many::contains)).containsExactly(many.get(0), many.get(1), many.get(2));
        // 동점이면 최근 공개·큰 ID
        long t1 = post(member("trtie1"), "동점1", Duration.ofHours(5), 1, 0);
        long t2 = post(member("trtie2"), "동점2", Duration.ofHours(5), 1, 0);
        List<Long> again = trending.rank(100);
        assertThat(again.indexOf(t2)).isLessThan(again.indexOf(t1));
    }

    @Test
    void snapshotPagingKeepsOrderSkipsHiddenAndExpires() throws Exception {
        clock.set(NOW);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ids.add(post(member("trpage" + i), "트렌딩" + i, Duration.ofHours(1 + i), 1, 0));
        }
        String first = mockMvc.perform(get("/api/posts/trending")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12)).andReturn().getResponse().getContentAsString();
        Set<Long> seen = new HashSet<>();
        collect(first, seen);
        String cursor = first.replaceAll("^.*\"nextCursor\":\"([^\"]+)\".*$", "$1");
        // 순위가 새로 계산돼도(뒤집힘) 보던 순위 그대로, 아직 안 본 글 하나는 비공개가 됨
        jdbc.update("UPDATE post SET like_count = 100 WHERE id = ?", ids.get(14));
        clock.set(NOW.plusSeconds(600));
        trending.refresh();
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", ids.get(13));
        String second = mockMvc.perform(get("/api/posts/trending?cursor=" + cursor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        collect(second, seen);
        assertThat(seen).hasSize(14).doesNotContain(ids.get(13));
        // 새 순위는 뒤집힌 글이 맨 앞
        mockMvc.perform(get("/api/posts/trending")).andExpect(jsonPath("$.items[0].id").value(ids.get(14)));
        // 사라진 순위는 410, 모양이 틀린 커서는 400
        redis.delete("trending:" + cursor.substring(0, cursor.indexOf(':')));
        mockMvc.perform(get("/api/posts/trending?cursor=" + cursor)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_EXPIRED"));
        mockMvc.perform(get("/api/posts/trending?cursor=abc")).andExpect(status().isBadRequest());
        String page = mockMvc.perform(get("/?tab=trending&cursor=" + cursor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("순위가 새로 바뀌었어요").contains("최근 7일 동안 반응이 많은 글 · 10분마다 갱신")
                .contains("?tab=trending&amp;cursor=").contains("data-expired-url=\"/?tab=trending&amp;expired=1\"");
    }

    @Test
    void emptyStateAndDefaultTab() throws Exception {
        clock.set(NOW);
        post(member("trquiet"), "반응 없음", Duration.ofHours(1), 0, 0);
        String trend = mockMvc.perform(get("/?tab=trending")).andReturn().getResponse().getContentAsString();
        assertThat(trend).contains("아직 트렌딩 글이 없어요").contains("최신 글 보기").doesNotContain("반응 없음");
        String home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("반응 없음").contains("<a href=\"/\" aria-current=\"page\">최신 글</a>");
    }

    private static void collect(String body, Set<Long> seen) {
        Matcher m = Pattern.compile("\"id\":(\\d+),\"url\"").matcher(body);
        while (m.find()) {
            assertThat(seen.add(Long.parseLong(m.group(1)))).isTrue();
        }
    }
}
