package com.team.blog.notification.application;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 정리(FR-032)와 탈퇴 30일 뒤 정리(FR-034). 탈퇴 정리는 사건이 아니라 익명 처리(023)가 글·댓글 단계 다음에 같은 묶음으로
 * 부른다.
 */
@Service
public class NotificationCleanup {

    private final JdbcTemplate jdbc;
    private final NotificationProperties properties;
    private final NotificationWriter writer;
    private final Clock clock;

    public NotificationCleanup(JdbcTemplate jdbc, NotificationProperties properties, NotificationWriter writer, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.writer = writer;
        this.clock = clock;
    }

    /** 90일 지난 알림(묶음 단위로 나눠), 받는 사람마다 최신 1,000개 밖 알림 삭제. @return 지운 수 */
    public int purge() {
        Timestamp before = Timestamp.from(clock.instant().minus(properties.retention()));
        int total = 0;
        int n;
        do {
            n = jdbc.update("""
                    DELETE FROM notification WHERE id IN (
                        SELECT id FROM notification WHERE updated_at < ? ORDER BY updated_at LIMIT ?)
                    """, before, properties.cleanupBatch());
            total += n;
        } while (n == properties.cleanupBatch());
        total += jdbc.update("""
                DELETE FROM notification WHERE id IN (
                    SELECT id FROM (
                        SELECT id, row_number() OVER (PARTITION BY receiver_id ORDER BY updated_at DESC, id DESC) AS rn
                        FROM notification
                        WHERE receiver_id IN (SELECT receiver_id FROM notification GROUP BY receiver_id HAVING count(*) > ?)
                    ) ranked WHERE rn > ?)
                """, properties.maxPerMember(), properties.maxPerMember());
        return total;
    }

    /** 익명 처리 단계(25 §8): ① 받은 알림 ② 남의 묶음에서 빼고 다시 계산 ③ 이 사람이 일으킨 하나짜리 알림. */
    @Transactional
    public void purgeWithdrawn(long memberId) {
        jdbc.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
        jdbc.queryForList("DELETE FROM notification_actor WHERE actor_id = ? RETURNING notification_id", Long.class, memberId)
                .stream().distinct().forEach(writer::recount);
        jdbc.update("DELETE FROM notification WHERE last_actor_id = ? AND group_key IS NULL", memberId);
    }
}
