package com.team.blog.discovery.application;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 작성자 통계(032, 강성찬 개인 확장, 31 W-7 `post_view_daily`): 본인만 보는 화면. 합계(발행 글·조회·좋아요·댓글·팔로워),
 * 최근 30일 날짜별 조회수, 30일 동안 많이 읽힌 글 5개. 조회수는 016 기준(새로고침·글쓴이·봇 제외)으로 센 값이다.
 */
@Service
public class AuthorStatsQuery {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M.d");

    public record Totals(long posts, long views, long likes, long comments, long followers) {
    }

    /** 막대 하나: 날짜, 조회수, 가장 큰 날 대비 높이(%). */
    public record DayViews(LocalDate date, int views, int percent) {

        public String label() {
            return DAY.format(date);
        }
    }

    public record TopPost(long id, String title, String url, int views) {
    }

    public record Stats(Totals totals, List<DayViews> days, int recentViews, List<TopPost> top) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthorStatsQuery(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Stats stats(long memberId) {
        Totals totals = jdbc.queryForObject("""
                SELECT count(*) AS posts, COALESCE(sum(view_count), 0) AS views, COALESCE(sum(like_count), 0) AS likes,
                       COALESCE(sum(comment_count), 0) AS comments,
                       (SELECT count(*) FROM follow f WHERE f.followee_id = ?) AS followers
                FROM post WHERE author_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL
                """, (rs, n) -> new Totals(rs.getLong("posts"), rs.getLong("views"), rs.getLong("likes"),
                rs.getLong("comments"), rs.getLong("followers")), memberId, memberId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
        LocalDate from = today.minusDays(29);
        Map<LocalDate, Integer> byDay = new HashMap<>();
        jdbc.query("""
                SELECT d.view_date, sum(d.views) AS views FROM post_view_daily d JOIN post p ON p.id = d.post_id
                WHERE p.author_id = ? AND d.view_date >= ? GROUP BY d.view_date
                """, rs -> {
            byDay.put(rs.getDate("view_date").toLocalDate(), rs.getInt("views"));
        }, memberId, Date.valueOf(from));
        int max = byDay.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<DayViews> days = new ArrayList<>();
        int recent = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            int v = byDay.getOrDefault(d, 0);
            recent += v;
            days.add(new DayViews(d, v, max == 0 ? 0 : Math.max(v > 0 ? 4 : 0, v * 100 / max)));
        }
        List<TopPost> top = jdbc.query("""
                SELECT p.id, p.title, m.handle, sum(d.views) AS views FROM post_view_daily d JOIN post p ON p.id = d.post_id
                JOIN member m ON m.id = p.author_id
                WHERE p.author_id = ? AND d.view_date >= ? AND p.deleted_at IS NULL
                GROUP BY p.id, p.title, m.handle ORDER BY views DESC, p.id DESC LIMIT 5
                """, (rs, n) -> new TopPost(rs.getLong("id"), rs.getString("title"),
                "/@" + rs.getString("handle") + "/posts/" + rs.getLong("id"), rs.getInt("views")), memberId, Date.valueOf(from));
        return new Stats(totals, days, recent, top);
    }
}
