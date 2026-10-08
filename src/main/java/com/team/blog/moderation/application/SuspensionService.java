package com.team.blog.moderation.application;

import com.team.blog.account.application.SessionRevoker;
import com.team.blog.shared.error.CannotReportOwnException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 정지(022 FR-038~FR-044). 1·7·30일 또는 영구, 사유 필수(200자). 정지하면 커밋 뒤 모든 기기 세션을 끊는다. 기간이 지나면
 * 로그인할 때 001 {@code AccountStatusChecker}가 자동으로 푼다(정기 작업 없음). 관리자·자기 자신은 정지할 수 없다. 알림 없음.
 */
@Service
public class SuspensionService {

    private static final Logger log = LoggerFactory.getLogger(SuspensionService.class);
    public static final Set<String> PERIODS = Set.of("1", "7", "30", "PERMANENT");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AdminGuard adminGuard;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;

    public SuspensionService(JdbcTemplate jdbc, TransactionTemplate tx, AdminGuard adminGuard, SessionRevoker sessionRevoker,
                             Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.adminGuard = adminGuard;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
    }

    public void suspend(Optional<CurrentUser> current, long memberId, String period, String reasonRaw) {
        CurrentUser admin = adminGuard.requireAdmin(current);
        String role = jdbc.query("SELECT role FROM member WHERE id = ? AND withdrawn_at IS NULL", (rs, n) -> rs.getString(1), memberId)
                .stream().findFirst().orElseThrow(NotFoundException::new);
        if (memberId == admin.memberId() || "ADMIN".equals(role)) {
            throw new CannotReportOwnException(CannotReportOwnException.MODERATE_OWN);
        }
        if (period == null || !PERIODS.contains(period)) {
            throw new ProfileValidationException(FieldError.of("period", "INVALID_SUSPENSION_PERIOD"));
        }
        String reason = reasonRaw == null ? "" : reasonRaw.strip();
        if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 200) {
            throw new ProfileValidationException(FieldError.of("reason", "SUSPENSION_REASON_REQUIRED"));
        }
        Instant now = clock.instant();
        Instant ends = "PERMANENT".equals(period) ? null : now.plus(Duration.ofDays(Long.parseLong(period)));
        tx.executeWithoutResult(status -> {
            // 진행 중인 정지는 새 정지로 바꾼다
            jdbc.update("UPDATE member_suspension SET lifted_at = ?, lifted_by = ? WHERE member_id = ? AND lifted_at IS NULL",
                    Timestamp.from(now), admin.memberId(), memberId);
            jdbc.update("""
                    INSERT INTO member_suspension (member_id, reason, started_at, ends_at, suspended_by) VALUES (?, ?, ?, ?, ?)
                    """, memberId, reason, Timestamp.from(now), ends == null ? null : Timestamp.from(ends), admin.memberId());
            jdbc.update("UPDATE member SET status = 'SUSPENDED', updated_at = ? WHERE id = ?", Timestamp.from(now), memberId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sessionRevoker.revokeAll(memberId);
                    } catch (RuntimeException e) {
                        log.warn("정지 회원 세션 삭제 실패: memberId={} cause={}", memberId, e.getClass().getSimpleName());
                    }
                }
            });
        });
    }

    public void lift(Optional<CurrentUser> current, long memberId) {
        CurrentUser admin = adminGuard.requireAdmin(current);
        tx.executeWithoutResult(status -> {
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.update("UPDATE member_suspension SET lifted_at = ?, lifted_by = ? WHERE member_id = ? AND lifted_at IS NULL",
                    now, admin.memberId(), memberId);
            jdbc.update("UPDATE member SET status = 'ACTIVE', updated_at = ? WHERE id = ? AND status = 'SUSPENDED'", now, memberId);
        });
    }
}
