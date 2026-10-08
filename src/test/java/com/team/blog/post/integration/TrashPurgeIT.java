package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.JobLock;
import com.team.blog.post.application.PostTrashService;
import com.team.blog.post.application.TrashPurgeJob;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 011 T1106: 휴지통 30일 자동 정리(FR-028). */
class TrashPurgeIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    PostTrashService trashService;

    @Autowired
    JobLock jobLock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long trashedAt(long author, Instant at) {
        long id = posts.published(author, "휴지통", "본문", 1, Instant.parse("2026-09-01T00:00:00Z"));
        jdbc.update("UPDATE post SET deleted_at = ? WHERE id = ?", Timestamp.from(at), id);
        return id;
    }

    @Test
    void purgesOnlyOlderThanThirtyDaysInBatches() {
        clock.set(NOW);
        long me = writer(members, "purgejob");
        long old = trashedAt(me, NOW.minus(Duration.ofDays(30)).minusSeconds(1));
        long young = trashedAt(me, NOW.minus(Duration.ofDays(29)));
        long live = posts.published(me, "살아 있음", "본문", 1, Instant.parse("2026-09-01T00:00:00Z"));
        for (int i = 0; i < 120; i++) {
            trashedAt(me, NOW.minus(Duration.ofDays(40)));
        }
        TrashPurgeJob job = new TrashPurgeJob(trashService, jobLock);
        redis.opsForValue().set("post:trash-purge-lock", "other", Duration.ofMinutes(5));
        job.run();
        assertThat(posts.exists(old)).isTrue();
        redis.delete("post:trash-purge-lock");
        job.run();
        assertThat(posts.exists(old)).isFalse();
        assertThat(posts.exists(young)).isTrue();
        assertThat(posts.exists(live)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post", Integer.class)).isEqualTo(2);
    }
}
