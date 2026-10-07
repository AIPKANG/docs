package com.team.blog.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 004: 발행(005) 없이 글 상태를 만든다. {@code ck_post_published}·{@code ck_post_public_at}을 만족하게 만든다. */
@TestComponent
public class PostFixtures {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public PostFixtures(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public long draft(long authorId, String title, String content, long version) {
        return jdbc.queryForObject("""
                INSERT INTO post (author_id, title, content_md, status, visibility, edit_version)
                VALUES (?, ?, ?, 'DRAFT', 'PUBLIC', ?) RETURNING id
                """, Long.class, authorId, title, content, version);
    }

    /** 공개 발행 상태로 바꾼다(제목이 비어 있으면 안 됨). */
    public void publish(long postId, Instant at) {
        jdbc.update("""
                UPDATE post SET status = 'PUBLISHED', published_at = ?, first_public_at = ?, content_html = '<p>발행본</p>'
                WHERE id = ?
                """, Timestamp.from(at), Timestamp.from(at), postId);
    }

    public long published(long authorId, String title, String content, long version, Instant at) {
        long id = draft(authorId, title, content, version);
        publish(id, at);
        return id;
    }

    public void trash(long postId) {
        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", postId);
    }

    public void setTimes(long postId, Instant createdAt, Instant updatedAt) {
        jdbc.update("UPDATE post SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(updatedAt), postId);
    }

    public void workingCopy(long postId, String title, String content, long version) {
        jdbc.update("INSERT INTO post_draft (post_id, title, content_md, edit_version) VALUES (?, ?, ?, ?)",
                postId, title, content, version);
    }

    public Map<String, Object> post(long postId) {
        return jdbc.queryForMap("SELECT * FROM post WHERE id = ?", postId);
    }

    public boolean exists(long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ?", Integer.class, postId) == 1;
    }

    /** 작업본 행(없으면 null). */
    public Map<String, Object> workingCopyRow(long postId) {
        return jdbc.queryForList("SELECT * FROM post_draft WHERE post_id = ?", postId).stream().findFirst().orElse(null);
    }

    public Map<Object, Object> buffer(long postId) {
        return redis.opsForHash().entries("autosave:post:" + postId);
    }

    public boolean dirty(long postId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember("autosave:dirty", String.valueOf(postId)));
    }

    /** 자동 저장 5초 1회 제한을 테스트 사이에서 비운다. */
    public void resetRateLimit(long memberId) {
        redis.delete("post:autosave:member:" + memberId);
    }

    public long count() {
        return jdbc.queryForObject("SELECT count(*) FROM post", Long.class);
    }
}
