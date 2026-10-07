package com.team.blog.support;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트마다 V1의 20개 테이블을 비우고 Redis {@code FLUSHDB}. */
@TestComponent
public class DatabaseCleaner {

    static final List<String> TABLES = List.of(
            "member", "image", "auth_identity", "member_agreement", "tag", "post", "post_draft", "post_like",
            "post_view_daily", "post_tag", "post_image", "comment", "follow", "friendship", "report_case", "report",
            "member_suspension", "notification", "notification_actor", "notification_mute");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public DatabaseCleaner(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public void clean() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE");
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
