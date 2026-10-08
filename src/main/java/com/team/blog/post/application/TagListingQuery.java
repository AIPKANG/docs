package com.team.blog.post.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.domain.TagNormalizer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 태그 읽기(013, 22 §5~§8). 공개 글 수는 006 공용 목록 조건(공개·발행·휴지통 아님·작성자 탈퇴 아님)으로만 센다 — 글 읽기 규칙이
 * 한곳에 있도록 post 모듈에 둔다(태그 테이블은 읽기만).
 */
@Service
public class TagListingQuery {

    private static final Logger log = LoggerFactory.getLogger(TagListingQuery.class);
    static final String TOP_KEY = "tags:top";

    public record TagCount(String name, long postCount) {
    }

    public record Suggestion(String name, long postCount, boolean mine) {
    }

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final AccountGuard accountGuard;
    private final RedisRateLimiter rateLimiter;

    public TagListingQuery(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, StringRedisTemplate redis,
                           JsonMapper jsonMapper, AccountGuard accountGuard, RedisRateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.accountGuard = accountGuard;
        this.rateLimiter = rateLimiter;
    }

    private String visible() {
        return accessPolicy.publicListingCondition("p", "m");
    }

    public Optional<Long> tagId(String normalizedName) {
        return jdbc.queryForList("SELECT id FROM tag WHERE name = ?", Long.class, normalizedName).stream().findFirst();
    }

    /** 태그의 공개 글 수(없는 태그는 0). */
    public long publicCount(String normalizedName) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM post_tag pt JOIN tag t ON t.id = pt.tag_id "
                + "JOIN post p ON p.id = pt.post_id JOIN member m ON m.id = p.author_id WHERE t.name = ? AND " + visible(),
                Long.class, normalizedName);
        return n == null ? 0 : n;
    }

    /** 전체 태그 상위 100개(공개 글 수 많은 순, 같으면 이름 순, 0개 제외). 10분 캐시(FR-019·FR-020). */
    public List<TagCount> top(int limit) {
        try {
            String cached = redis.opsForValue().get(TOP_KEY);
            if (cached != null) {
                List<TagCount> all = jsonMapper.readValue(cached, new TypeReference<List<TagCount>>() { });
                return all.subList(0, Math.min(limit, all.size()));
            }
        } catch (DataAccessException | tools.jackson.core.JacksonException e) {
            log.warn("tag top cache unavailable: {}", e.getMessage());
        }
        List<TagCount> computed = computeTop(100);
        try {
            redis.opsForValue().set(TOP_KEY, jsonMapper.writeValueAsString(computed), Duration.ofMinutes(10));
        } catch (DataAccessException e) {
            log.warn("tag top cache not stored: {}", e.getMessage());
        }
        return computed.subList(0, Math.min(limit, computed.size()));
    }

    List<TagCount> computeTop(int limit) {
        return jdbc.query("SELECT t.name, count(*) AS n FROM tag t JOIN post_tag pt ON pt.tag_id = t.id "
                + "JOIN post p ON p.id = pt.post_id JOIN member m ON m.id = p.author_id WHERE " + visible()
                + " GROUP BY t.name ORDER BY n DESC, t.name LIMIT ?", (rs, i) -> new TagCount(rs.getString(1), rs.getLong(2)), limit);
    }

    /** 블로그 태그 줄(FR-025): 블로그 목록과 같은 조건의 글에 쓰인 태그, 글 수 많은 순. */
    public List<TagCount> blogTags(long authorId, int limit) {
        return jdbc.query("SELECT t.name, count(*) AS n FROM tag t JOIN post_tag pt ON pt.tag_id = t.id "
                + "JOIN post p ON p.id = pt.post_id JOIN member m ON m.id = p.author_id WHERE " + visible()
                + " AND p.author_id = ? GROUP BY t.name ORDER BY n DESC, t.name LIMIT ?",
                (rs, i) -> new TagCount(rs.getString(1), rs.getLong(2)), authorId, limit);
    }

    /**
     * 자동완성(FR-021~FR-024): 로그인 회원, 1분 60번. 후보는 내 태그(내 글 전부)와 공개 글 수 1 이상인 태그만, 앞부분 일치,
     * 내 태그 먼저 그다음 공개 글 수 많은 순, 10개.
     */
    public List<Suggestion> suggest(Optional<CurrentUser> currentUser, String q) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        RedisRateLimiter.Result limit = rateLimiter.tryAcquire(
                RedisRateLimiter.key("tag:suggest:member", String.valueOf(user.memberId())), 60, Duration.ofMinutes(1));
        if (!limit.allowed()) {
            throw new RateLimitedException(limit.retryAfterSeconds());
        }
        String prefix = TagNormalizer.shape(q);
        if (prefix.isEmpty()) {
            return List.of();
        }
        String like = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
                WITH pub AS (
                    SELECT pt.tag_id, count(*) AS n FROM post_tag pt JOIN post p ON p.id = pt.post_id
                    JOIN member m ON m.id = p.author_id WHERE """ + " " + visible() + """
                     GROUP BY pt.tag_id),
                mine AS (
                    SELECT DISTINCT pt.tag_id FROM post_tag pt JOIN post p ON p.id = pt.post_id WHERE p.author_id = ?)
                SELECT t.name, COALESCE(pub.n, 0) AS n, (mine.tag_id IS NOT NULL) AS mine
                FROM tag t LEFT JOIN pub ON pub.tag_id = t.id LEFT JOIN mine ON mine.tag_id = t.id
                WHERE t.name LIKE ? ESCAPE '\\' AND (mine.tag_id IS NOT NULL OR COALESCE(pub.n, 0) > 0)
                ORDER BY (mine.tag_id IS NOT NULL) DESC, n DESC, t.name
                LIMIT 10
                """, (rs, i) -> new Suggestion(rs.getString("name"), rs.getLong("n"), rs.getBoolean("mine")),
                user.memberId(), like);
    }
}
