package com.team.blog.moderation.application;

import com.team.blog.post.application.PostCounters;
import com.team.blog.shared.error.CannotReportOwnException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportsResolved;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 처리(022 FR-016~FR-028). 숨기기·문제없음은 그 대상의 대기 신고를 한 번에 닫고, 처리한 관리자·시각과 숨긴 관리자·시각·사유를
 * 기록한다. 내용은 바꾸지 않는다. 자기 것·자기가 신고자인 신고는 처리할 수 없다. 숨김 해제는 기록을 비우고 댓글 수를 되돌리며
 * 알림을 보내지 않는다. 알림 사건은 커밋 뒤에만 나간다.
 */
@Service
public class ModerationService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AdminGuard adminGuard;
    private final PostCounters postCounters;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ModerationService(JdbcTemplate jdbc, TransactionTemplate tx, AdminGuard adminGuard, PostCounters postCounters,
                             ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.adminGuard = adminGuard;
        this.postCounters = postCounters;
        this.events = events;
        this.clock = clock;
    }

    record Case(long id, String type, Long postId, Long commentId, long authorId, String status) {
    }

    private Optional<Case> lockCase(long caseId) {
        return jdbc.query("SELECT id, target_type, post_id, comment_id, target_author_id, status FROM report_case WHERE id = ? FOR UPDATE",
                (rs, n) -> new Case(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3), (Long) rs.getObject(4),
                        rs.getLong(5), rs.getString(6)), caseId).stream().findFirst();
    }

    private void checkNotOwn(CurrentUser admin, Case c) {
        boolean reporter = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM report WHERE case_id = ? AND reporter_id = ?)", Boolean.class, c.id(), admin.memberId()));
        if (c.authorId() == admin.memberId() || reporter) {
            throw new CannotReportOwnException(CannotReportOwnException.MODERATE_OWN);
        }
    }

    /** 숨기기: 대상을 숨기고 대기 신고를 모두 "숨김"으로 닫는다. */
    public void hide(Optional<CurrentUser> current, long caseId, String reasonRaw) {
        CurrentUser admin = adminGuard.requireAdmin(current);
        ReportReason reason = ReportReason.parse(reasonRaw)
                .orElseThrow(() -> new ProfileValidationException(FieldError.of("reason", "INVALID_HIDE_REASON")));
        tx.executeWithoutResult(status -> {
            Case c = lockCase(caseId).orElseThrow(NotFoundException::new);
            checkNotOwn(admin, c);
            if (!"PENDING".equals(c.status())) {
                throw new ProfileValidationException(FieldError.of("case", "CASE_ALREADY_HANDLED"));
            }
            Timestamp now = Timestamp.from(clock.instant());
            if (c.postId() != null) {
                int n = jdbc.update("UPDATE post SET hidden_at = ?, hidden_by = ?, hidden_reason = ? WHERE id = ? AND hidden_at IS NULL",
                        now, admin.memberId(), reason.name(), c.postId());
                if (n == 1) {
                    events.publishEvent(new ContentHidden(c.postId(), null, c.authorId()));
                }
            } else if (c.commentId() != null) {
                Long postId = jdbc.query("""
                        UPDATE comment SET hidden_at = ?, hidden_by = ?, hidden_reason = ?
                        WHERE id = ? AND hidden_at IS NULL AND deleted_at IS NULL RETURNING post_id
                        """, (rs, n) -> rs.getLong(1), now, admin.memberId(), reason.name(), c.commentId())
                        .stream().findFirst().orElse(null);
                if (postId != null) {
                    postCounters.adjustComments(postId, -1);
                    events.publishEvent(new ContentHidden(postId, c.commentId(), c.authorId()));
                }
            }
            close(c, "HIDDEN", admin, now, "ACTION_TAKEN");
        });
    }

    /** 문제없음: 대기 신고를 모두 "반려"로 닫는다. */
    public void reject(Optional<CurrentUser> current, long caseId) {
        CurrentUser admin = adminGuard.requireAdmin(current);
        tx.executeWithoutResult(status -> {
            Case c = lockCase(caseId).orElseThrow(NotFoundException::new);
            checkNotOwn(admin, c);
            if (!"PENDING".equals(c.status())) {
                throw new ProfileValidationException(FieldError.of("case", "CASE_ALREADY_HANDLED"));
            }
            close(c, "REJECTED", admin, Timestamp.from(clock.instant()), "NO_VIOLATION");
        });
    }

    private void close(Case c, String status, CurrentUser admin, Timestamp now, String result) {
        jdbc.update("UPDATE report_case SET status = ?, handled_by = ?, handled_at = ?, closed_at = ? WHERE id = ?",
                status, admin.memberId(), now, now, c.id());
        List<ReportsResolved.Report> reports = jdbc.query("SELECT id, reporter_id FROM report WHERE case_id = ?",
                (rs, n) -> new ReportsResolved.Report(rs.getLong(1), rs.getLong(2)), c.id());
        events.publishEvent(new ReportsResolved(reports, result));
    }

    /** 숨김 해제(FR-027·FR-028): 숨김 기록을 비우고, 댓글이면 댓글 수를 되돌린다. 알림 없음. */
    public void unhide(Optional<CurrentUser> current, long caseId) {
        CurrentUser admin = adminGuard.requireAdmin(current);
        tx.executeWithoutResult(status -> {
            Case c = lockCase(caseId).orElseThrow(NotFoundException::new);
            checkNotOwn(admin, c);
            if (c.postId() != null) {
                jdbc.update("UPDATE post SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL WHERE id = ?", c.postId());
            } else if (c.commentId() != null) {
                jdbc.query("""
                        UPDATE comment SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL
                        WHERE id = ? AND hidden_at IS NOT NULL AND deleted_at IS NULL RETURNING post_id
                        """, (rs, n) -> rs.getLong(1), c.commentId())
                        .forEach(postId -> postCounters.adjustComments(postId, +1));
            }
        });
    }
}
