package com.team.blog.discovery.application;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모아 둔 조회 반영(016 FR-009~FR-014, 31 §5-2). {@code view:pending:{날짜}}를 {@code view:processing:{날짜}:{시각}}으로 RENAME한 뒤
 * 글마다 한 트랜잭션(누적 조회수 + 날짜별 조회수)으로 반영하고 그 글만 {@code HDEL}. 반영 도중 죽으면 남은 처리 중 키를 다음에 먼저
 * 처리한다. {@code updated_at}은 건드리지 않는다.
 */
@Service
public class ViewFlusher {

    private static final Logger log = LoggerFactory.getLogger(ViewFlusher.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ViewFlusher(StringRedisTemplate redis, JdbcTemplate jdbc, TransactionTemplate transactionTemplate, Clock clock) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    private List<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    /** 한 번 반영. 반영한 (글, 날짜) 수. */
    public int flush() {
        int done = 0;
        // 지난번에 처리 중이던 키부터
        for (String processing : keys("view:processing:*")) {
            done += process(processing);
        }
        long stamp = clock.millis();
        for (String pending : keys("view:pending:*")) {
            String day = pending.substring("view:pending:".length());
            String processing = "view:processing:" + day + ":" + stamp;
            try {
                redis.rename(pending, processing);
            } catch (RuntimeException e) {
                continue; // 그사이 사라짐
            }
            done += process(processing);
        }
        return done;
    }

    private int process(String processingKey) {
        String day = processingKey.split(":")[2];
        LocalDate date = LocalDate.parse(day, DAY);
        Map<Object, Object> counts = redis.opsForHash().entries(processingKey);
        int done = 0;
        for (Map.Entry<Object, Object> e : counts.entrySet()) {
            long postId;
            long n;
            try {
                postId = Long.parseLong((String) e.getKey());
                n = Long.parseLong((String) e.getValue());
            } catch (NumberFormatException ex) {
                redis.opsForHash().delete(processingKey, e.getKey());
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    int updated = jdbc.update("UPDATE post SET view_count = view_count + ? WHERE id = ?", n, postId);
                    if (updated == 1) {
                        jdbc.update("""
                                INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, ?)
                                ON CONFLICT (post_id, view_date) DO UPDATE SET views = post_view_daily.views + EXCLUDED.views
                                """, postId, Date.valueOf(date), n);
                    }
                });
                redis.opsForHash().delete(processingKey, e.getKey());
                done++;
            } catch (RuntimeException ex) {
                log.warn("view flush failed for post {} (retry next run): {}", postId, ex.getClass().getSimpleName());
            }
        }
        return done;
    }

    /** 90일 지난 날짜별 조회수 삭제(FR-015). */
    public int purgeOldDaily(java.time.Duration retention) {
        LocalDate cutoff = LocalDate.ofInstant(clock.instant(), ViewRecorder.SEOUL).minusDays(retention.toDays());
        return jdbc.update("DELETE FROM post_view_daily WHERE view_date < ?", Date.valueOf(cutoff));
    }
}
