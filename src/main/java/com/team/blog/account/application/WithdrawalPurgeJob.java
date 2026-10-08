package com.team.blog.account.application;

import com.team.blog.post.application.JobLock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 03:00, 탈퇴 신청 후 30일이 지났고 아직 정리되지 않은 회원을 정리한다(023 FR-026~FR-036). 회원 1명이 한 트랜잭션이라
 * 한 단계라도 실패하면 그 회원만 취소되고 다음 날 다시 한다. 정리가 확정된 뒤 세션을 끊는다. 별도 알림은 없다.
 */
@Component
public class WithdrawalPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPurgeJob.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final List<WithdrawalPurgeStep> steps;
    private final SessionRevoker sessionRevoker;
    private final JobLock jobLock;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalPurgeJob(JdbcTemplate jdbc, TransactionTemplate tx, List<WithdrawalPurgeStep> steps,
                              SessionRevoker sessionRevoker, JobLock jobLock, WithdrawalProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.steps = steps.stream().sorted(Comparator.comparingInt(WithdrawalPurgeStep::order)).toList();
        this.sessionRevoker = sessionRevoker;
        this.jobLock = jobLock;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${blog.withdraw.purge-cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (properties.purgeEnabled()) {
            jobLock.runExclusively("withdraw:purge-lock", Duration.ofMinutes(30), this::run);
        }
    }

    /** @return 정리한 회원 수 */
    public int run() {
        List<Long> due = jdbc.queryForList("""
                SELECT id FROM member WHERE status = 'WITHDRAWN' AND deleted_at IS NULL AND withdrawn_at < ?
                ORDER BY withdrawn_at, id LIMIT ?
                """, Long.class, Timestamp.from(clock.instant().minus(properties.grace())), properties.purgeBatch());
        int done = 0;
        for (Long memberId : due) {
            try {
                tx.executeWithoutResult(status -> {
                    // 그사이 복구했으면 건너뛴다
                    List<Long> locked = jdbc.queryForList("""
                            SELECT id FROM member WHERE id = ? AND status = 'WITHDRAWN' AND deleted_at IS NULL FOR UPDATE
                            """, Long.class, memberId);
                    if (locked.isEmpty()) {
                        return;
                    }
                    steps.forEach(step -> step.purge(memberId));
                });
                done++;
                try {
                    sessionRevoker.revokeAll(memberId);
                } catch (RuntimeException e) {
                    log.warn("정리 후 세션 삭제 실패: memberId={}", memberId);
                }
            } catch (RuntimeException e) {
                log.warn("탈퇴 정리 실패(다음 실행에서 다시): memberId={} cause={}", memberId, e.getClass().getSimpleName());
            }
        }
        return done;
    }
}
