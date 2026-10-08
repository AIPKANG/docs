package com.team.blog.discovery.application;

import com.team.blog.account.application.ProfileAvatar;
import com.team.blog.post.application.PostAccessPolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 홈 "새로 온 작가"(036, 강성찬 개인 확장, 01 비교표 "첫 글·새 작가"): 최근 기간 안에 처음으로 공개 글을 올린 사람과 그 첫 글.
 * 공용 목록 조건을 거친 글만 센다(지금 공개인 글 중 가장 먼저 공개된 글이 첫 글).
 */
@Service
public class NewcomerQuery {

    public record Newcomer(String handle, String nickname, String profileImageUrl, long postId, String title) {

        public ProfileAvatar avatar() {
            return new ProfileAvatar(handle, nickname, profileImageUrl);
        }

        public String url() {
            return "/@" + handle + "/posts/" + postId;
        }
    }

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final NewcomerProperties properties;
    private final Clock clock;

    public NewcomerQuery(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, NewcomerProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public List<Newcomer> recent() {
        Timestamp since = Timestamp.from(clock.instant().minus(properties.window()));
        return jdbc.query("""
                SELECT handle, nickname, profile_image_url, id, title FROM (
                  SELECT DISTINCT ON (p.author_id) p.author_id, p.id, p.title, p.first_public_at, m.handle, m.nickname, m.profile_image_url
                  FROM post p JOIN member m ON m.id = p.author_id
                  WHERE """ + " " + accessPolicy.publicListingCondition("p", "m") + """
                  ORDER BY p.author_id, p.first_public_at, p.id
                ) firsts WHERE first_public_at >= ? ORDER BY first_public_at DESC LIMIT """ + " " + properties.limit(),
                (rs, n) -> new Newcomer(rs.getString("handle"), rs.getString("nickname"), rs.getString("profile_image_url"),
                        rs.getLong("id"), rs.getString("title")), since);
    }
}
