package com.team.blog.discovery.application;

import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.post.application.PostCard;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.SnapshotExpiredException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 트렌딩(019, 32 §2~§4). 10분마다 상위 100개를 계산해 Redis 목록 {@code trending:{스냅샷}}(30분)에 두고 {@code trending:current}가
 * 가리킨다. 넘기기 커서는 {@code {스냅샷}:{위치}}라 보던 순위 그대로 이어지고, 그 사이 볼 수 없게 된 글은 건너뛰어 9개를 채운다.
 * 사라진 스냅샷은 410, Redis를 못 쓰면 DB에서 바로 계산해 첫 9개만(다음 없음).
 */
@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);
    static final String CURRENT = "trending:current";
    private static final DateTimeFormatter SNAPSHOT_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final PostListQuery listQuery;
    private final PostAccessPolicy accessPolicy;
    private final TrendingProperties properties;
    private final Clock clock;

    public TrendingService(JdbcTemplate jdbc, StringRedisTemplate redis, PostListQuery listQuery, PostAccessPolicy accessPolicy,
                           TrendingProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.listQuery = listQuery;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    /** 32 §3-1: 공용 목록 조건 + 숨김 아님 + 7일, 반응 최소 조건, 작성자당 3개, 점수·공개 시각·ID 순 상위 N. */
    public List<Long> rank(int limit) {
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        return jdbc.queryForList("""
                WITH scored AS (
                    SELECT p.id, p.author_id, p.first_public_at, p.like_count AS likes, p.view_count AS views,
                           (SELECT count(DISTINCT c.author_id) FROM comment c
                             WHERE c.post_id = p.id AND c.author_id <> p.author_id
                               AND c.deleted_at IS NULL AND c.hidden_at IS NULL) AS commenters
                    FROM post p JOIN member m ON m.id = p.author_id
                    WHERE """ + " " + accessPolicy.publicListingCondition("p", "m") + """

                      AND p.hidden_at IS NULL AND p.first_public_at >= ?
                ), ranked AS (
                    SELECT *, (? * likes + ? * commenters + ? * views)
                              / power(GREATEST(extract(epoch FROM (?::timestamptz - first_public_at)), 0) / 3600 + ?, ?) AS score
                    FROM scored
                    WHERE likes >= 1 OR commenters >= 1
                ), capped AS (
                    SELECT *, row_number() OVER (PARTITION BY author_id ORDER BY score DESC, first_public_at DESC, id DESC) AS rn
                    FROM ranked
                )
                SELECT id FROM capped WHERE rn <= ?
                ORDER BY score DESC, first_public_at DESC, id DESC
                LIMIT ?
                """, Long.class, Timestamp.from(now.minus(properties.window())), properties.likeWeight(),
                properties.commenterWeight(), properties.viewWeight(), nowTs, properties.hourOffset(), properties.gravity(),
                properties.perAuthor(), limit);
    }

    /** 새 스냅샷을 만들고 현재로 바꾼다. @return 스냅샷 ID */
    public String refresh() {
        List<Long> ids = rank(properties.top());
        String id = SNAPSHOT_ID.format(clock.instant());
        String key = "trending:" + id;
        redis.delete(key);
        if (!ids.isEmpty()) {
            redis.opsForList().rightPushAll(key, ids.stream().map(String::valueOf).toList());
        } else {
            // 빈 순위도 스냅샷으로 남긴다(빈 상태를 커서로 구분)
            redis.opsForList().rightPush(key, "");
        }
        redis.expire(key, properties.snapshotTtl());
        redis.opsForValue().set(CURRENT, id, properties.snapshotTtl());
        return id;
    }

    /**
     * 한 페이지. {@code cursor}가 없으면 현재 스냅샷 처음부터(없으면 지금 만든다).
     *
     * @throws SnapshotExpiredException 커서의 스냅샷이 사라짐(410)
     * @throws PostContentException     커서 모양이 틀림(400 {@code INVALID_CURSOR})
     */
    public CardPage page(String cursor) {
        String snapshot;
        int position;
        try {
            if (cursor == null || cursor.isEmpty()) {
                snapshot = redis.opsForValue().get(CURRENT);
                if (snapshot == null || Boolean.FALSE.equals(redis.hasKey("trending:" + snapshot))) {
                    snapshot = refresh();
                }
                position = 0;
            } else {
                int colon = cursor.lastIndexOf(':');
                if (colon <= 0 || !cursor.substring(0, colon).matches("\\d{17}") || !cursor.substring(colon + 1).matches("\\d{1,3}")) {
                    throw new PostContentException("INVALID_CURSOR");
                }
                snapshot = cursor.substring(0, colon);
                position = Integer.parseInt(cursor.substring(colon + 1));
            }
            return fromSnapshot(snapshot, position);
        } catch (DataAccessException e) {
            log.warn("trending snapshot unavailable, computing directly: {}", e.getClass().getSimpleName());
            if (cursor != null && !cursor.isEmpty()) {
                throw new SnapshotExpiredException();
            }
            List<Long> ids = rank(properties.pageSize());
            Map<Long, PostCard> cards = listQuery.cardsByIds(ids);
            return new CardPage(ids.stream().map(cards::get).filter(java.util.Objects::nonNull).toList(), null);
        }
    }

    private CardPage fromSnapshot(String snapshot, int position) {
        String key = "trending:" + snapshot;
        Long size = redis.opsForList().size(key);
        if (size == null || size == 0) {
            throw new SnapshotExpiredException();
        }
        int pageSize = properties.pageSize();
        List<PostCard> items = new ArrayList<>();
        int pos = position;
        while (items.size() < pageSize && pos < size) {
            int need = pageSize - items.size();
            List<String> raw = redis.opsForList().range(key, pos, pos + need - 1);
            if (raw == null || raw.isEmpty()) {
                break;
            }
            List<Long> ids = raw.stream().filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
            Map<Long, PostCard> cards = listQuery.cardsByIds(ids);
            for (Long id : ids) {
                PostCard card = cards.get(id);
                if (card != null) {
                    items.add(card);
                }
            }
            pos += raw.size();
        }
        String next = pos < size ? snapshot + ":" + pos : null;
        return new CardPage(items, next);
    }
}
