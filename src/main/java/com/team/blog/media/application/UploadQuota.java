package com.team.blog.media.application;

import com.team.blog.shared.error.DailyUploadLimitException;
import com.team.blog.shared.error.StorageQuotaExceededException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원별 사진 한도(23 §3, 008 FR-023~FR-025): 하루 200장(한국 시간 0시 기준, 실패한 업로드도 셈)과 저장 공간 1GB
 * (그 회원이 올린 모든 사진의 원본 + 썸네일 합계). 동시 승인은 회원별 트랜잭션 잠금({@code pg_advisory_xact_lock})으로
 * 한 번에 하나씩 판정한다 — media는 회원 테이블을 잠그지 않는다(헌법 I).
 */
@Component
public class UploadQuota {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    /** advisory lock 키 공간(다른 잠금과 겹치지 않게). */
    private static final int LOCK_SPACE = 8008;

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ImageProperties properties;

    public UploadQuota(StringRedisTemplate redis, JdbcTemplate jdbc, ImageProperties properties) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    static String dailyKey(long memberId, Instant now) {
        return "img:daily:" + memberId + ":" + LocalDate.ofInstant(now, SEOUL).format(DAY);
    }

    /** 오늘 장수를 1 올리고 한도를 넘으면 거부(올린 수는 되돌리지 않음 — 실패한 업로드도 센다). */
    public void countDailyUpload(long memberId, Instant now) {
        String key = dailyKey(memberId, now);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > properties.dailyLimit()) {
            Instant midnight = LocalDate.ofInstant(now, SEOUL).plusDays(1).atStartOfDay(SEOUL).toInstant();
            throw new DailyUploadLimitException(Duration.between(now, midnight).toSeconds());
        }
    }

    public int todayCount(long memberId, Instant now) {
        String value = redis.opsForValue().get(dailyKey(memberId, now));
        return value == null ? 0 : Integer.parseInt(value);
    }

    /** 트랜잭션 안에서: 회원별 잠금 후 지금 사용량 + {@code additionalBytes}가 한도를 넘으면 거부. */
    public void lockAndCheck(long memberId, long additionalBytes) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, ?)", Object.class, LOCK_SPACE, (int) memberId);
        if (usedBytes(memberId) + additionalBytes > properties.quotaBytes()) {
            throw new StorageQuotaExceededException();
        }
    }

    public long usedBytes(long memberId) {
        Long used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(size_bytes::bigint + COALESCE(thumb_size_bytes, 0)), 0) FROM image WHERE uploader_id = ?
                """, Long.class, memberId);
        return used == null ? 0 : used;
    }
}
