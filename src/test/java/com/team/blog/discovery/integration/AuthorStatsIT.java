package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.discovery.application.AuthorStatsQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 032 작성자 통계(강성찬 개인 확장): 본인만, 합계·30일 조회·많이 읽힌 글. */
class AuthorStatsIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    @Autowired
    AuthorStatsQuery statsQuery;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ownerSeesTotalsDailyViewsAndTopPosts() throws Exception {
        clock.set(NOW);
        long me = members.localMember("statsme", "statsme", "statsme@example.com", "Blog#2026ok", true);
        long other = members.localMember("statsother", "statsother", "statsother@example.com", "Blog#2026ok", true);
        long a = posts.published(me, "많이 읽힌 글", "본문", 1, NOW);
        long b = posts.published(me, "조금 읽힌 글", "본문", 1, NOW);
        long theirs = posts.published(other, "남의 글", "본문", 1, NOW);
        jdbc.update("UPDATE post SET view_count = 30, like_count = 4, comment_count = 2 WHERE id = ?", a);
        jdbc.update("UPDATE post SET view_count = 5 WHERE id = ?", b);
        LocalDate today = LocalDate.of(2026, 10, 9);
        jdbc.update("INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, 20), (?, ?, 10), (?, ?, 5), (?, ?, 99)",
                a, Date.valueOf(today), a, Date.valueOf(today.minusDays(3)), b, Date.valueOf(today), theirs, Date.valueOf(today));
        jdbc.update("INSERT INTO follow (follower_id, followee_id) VALUES (?, ?)", other, me);
        AuthorStatsQuery.Stats s = statsQuery.stats(me);
        assertThat(s.totals()).isEqualTo(new AuthorStatsQuery.Totals(2, 35, 4, 2, 1));
        assertThat(s.days()).hasSize(30);
        assertThat(s.recentViews()).isEqualTo(35);
        assertThat(s.days().get(29).views()).isEqualTo(25);
        assertThat(s.days().get(29).percent()).isEqualTo(100);
        assertThat(s.top()).extracting(AuthorStatsQuery.TopPost::title).containsExactly("많이 읽힌 글", "조금 읽힌 글");
        String html = mockMvc.perform(get("/manage/stats").with(TestAuth.member(me))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("최근 30일 조회 35").contains("많이 읽힌 글").doesNotContain("남의 글");
        assertThat(mockMvc.perform(get("/manage/stats")).andReturn().getResponse().getHeader("Location")).contains("/login");
    }
}
