package com.team.blog.moderation.application;

import com.team.blog.post.application.JobLock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대상이 완전 삭제된 대기 신고를 "대상 없음"으로 닫고(FR-035, 알림 없음), 닫힌 지 30일이 지난 "대상 없음" 묶음의 복사본을 지운다.
 * 테스트는 끄고 {@link #run()}을 직접 부른다.
 */
@Component
public class ReportCleanupJob {

    private final JdbcTemplate jdbc;
    private final JobLock jobLock;
    private final ModerationProperties properties;
    private final Clock clock;

    public ReportCleanupJob(JdbcTemplate jdbc, JobLock jobLock, ModerationProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.jobLock = jobLock;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${blog.moderation.cleanup-cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (properties.cleanupEnabled()) {
            jobLock.runExclusively("moderation:cleanup-lock", Duration.ofMinutes(5), this::run);
        }
    }

    public void run() {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                UPDATE report_case SET status = 'CLOSED_NO_TARGET', closed_at = ?
                WHERE status = 'PENDING' AND post_id IS NULL AND comment_id IS NULL
                """, now);
        jdbc.update("""
                UPDATE report_case SET snapshot_title = NULL, snapshot_content = NULL
                WHERE status = 'CLOSED_NO_TARGET' AND closed_at < ? AND (snapshot_title IS NOT NULL OR snapshot_content IS NOT NULL)
                """, Timestamp.from(clock.instant().minus(properties.snapshotRetention())));
    }

    /** 익명 처리(023)가 부른다: 그 회원 콘텐츠의 대기 신고를 "대상 없음"으로(FR-036). */
    public int closeForWithdrawn(long memberId) {
        return jdbc.update("UPDATE report_case SET status = 'CLOSED_NO_TARGET', closed_at = ? WHERE status = 'PENDING' AND target_author_id = ?",
                Timestamp.from(clock.instant()), memberId);
    }
}
