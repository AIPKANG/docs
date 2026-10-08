package com.team.blog.moderation.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.error.CannotReportOwnException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 신고 접수(022 FR-001~FR-010, 43 §2). 판정 순서: 로그인(401) → 인증 전·탈퇴 유예(403) → 대상을 볼 수 있는지(404, 없음과 같음)
 * → 자기 것(400) → 사유·설명(400) → 1분 5건·하루 50건(429) → 같은 사람·같은 대상이면 새로 만들지 않고 성공.
 * 대상마다 대기 묶음({@code report_case}) 하나에 신고가 모이고, 묶음을 처음 만들 때 그 시점 내용을 복사해 둔다.
 * 신고 수와 상관없이 자동으로 숨기지 않는다.
 */
@Service
public class ReportService {

    public record ReportResult(boolean created) {
    }

    private record Target(String type, Long postId, Long commentId, long authorId, String title, String content) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AccountGuard accountGuard;
    private final PostReadAccess postReadAccess;
    private final RedisRateLimiter rateLimiter;
    private final ModerationProperties properties;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, TransactionTemplate tx, AccountGuard accountGuard, PostReadAccess postReadAccess,
                         RedisRateLimiter rateLimiter, ModerationProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.accountGuard = accountGuard;
        this.postReadAccess = postReadAccess;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    public ReportResult report(Optional<CurrentUser> current, String targetType, long targetId, String reasonRaw,
                               String detailRaw) {
        CurrentUser user = accountGuard.requireWritable(current);
        Target target = target(current, targetType, targetId);
        if (target.authorId() == user.memberId()) {
            throw new CannotReportOwnException(CannotReportOwnException.CODE);
        }
        ReportReason reason = ReportReason.parse(reasonRaw)
                .orElseThrow(() -> new ProfileValidationException(FieldError.of("reason", "INVALID_REPORT_REASON")));
        String detail = detailRaw == null ? null : detailRaw.strip();
        if (detail != null && detail.isEmpty()) {
            detail = null;
        }
        if (reason == ReportReason.OTHER && detail == null) {
            throw new ProfileValidationException(FieldError.of("detail", "REPORT_DETAIL_REQUIRED"));
        }
        if (detail != null && detail.codePointCount(0, detail.length()) > 200) {
            throw new ProfileValidationException(FieldError.of("detail", "REPORT_DETAIL_TOO_LONG"));
        }
        String column = target.postId() != null ? "post_id" : "comment_id";
        long ref = target.postId() != null ? target.postId() : target.commentId();
        Boolean already = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM report r JOIN report_case c ON c.id = r.case_id "
                + "WHERE c." + column + " = ? AND r.reporter_id = ?)", Boolean.class, ref, user.memberId());
        if (Boolean.TRUE.equals(already)) {
            return new ReportResult(false);
        }
        limit("report:minute", user.memberId(), properties.reportsPerMinute(), Duration.ofMinutes(1));
        limit("report:day", user.memberId(), properties.reportsPerDay(), Duration.ofDays(1));
        String d = detail;
        Timestamp now = Timestamp.from(clock.instant());
        Boolean created = tx.execute(status -> {
            // 같은 대상의 첫 신고가 동시에 와도 대기 묶음은 하나
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(9022, hashtext(?))", Object.class, column + ":" + ref);
            Long caseId = jdbc.query("SELECT id FROM report_case WHERE " + column + " = ? AND status = 'PENDING'",
                    (rs, n) -> rs.getLong(1), ref).stream().findFirst().orElseGet(() -> jdbc.queryForObject("""
                    INSERT INTO report_case (target_type, post_id, comment_id, target_author_id, snapshot_title, snapshot_content, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, target.type(), target.postId(), target.commentId(), target.authorId(), target.title(),
                    target.content(), now));
            return jdbc.update("""
                    INSERT INTO report (case_id, reporter_id, reason, detail, created_at) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (case_id, reporter_id) DO NOTHING
                    """, caseId, user.memberId(), reason.name(), d, now) == 1;
        });
        return new ReportResult(Boolean.TRUE.equals(created));
    }

    /** 신고자가 지금 볼 수 있는 대상만(숨김·삭제된 댓글, 볼 수 없는 글은 404). 내용은 신고 시점 복사본. */
    private Target target(Optional<CurrentUser> viewer, String type, long id) {
        if ("POST".equals(type)) {
            PostReadAccess.ReadablePost post = postReadAccess.requireWritableTarget(viewer, id);
            return jdbc.query("SELECT title, content_md FROM post WHERE id = ?", (rs, n) -> new Target("POST", id, null,
                    post.authorId(), rs.getString("title"), cut(rs.getString("content_md"))), id).getFirst();
        }
        if ("COMMENT".equals(type)) {
            record Row(long postId, long authorId, String content) {
            }
            Row row = jdbc.query("""
                    SELECT post_id, author_id, content FROM comment WHERE id = ? AND deleted_at IS NULL AND hidden_at IS NULL
                    """, (rs, n) -> new Row(rs.getLong(1), rs.getLong(2), rs.getString(3)), id)
                    .stream().findFirst().orElseThrow(NotFoundException::new);
            postReadAccess.requireWritableTarget(viewer, row.postId());
            return new Target("COMMENT", null, id, row.authorId(), null, row.content());
        }
        throw new NotFoundException();
    }

    private String cut(String content) {
        if (content == null) {
            return null;
        }
        int max = properties.snapshotChars();
        return content.codePointCount(0, content.length()) <= max ? content
                : content.substring(0, content.offsetByCodePoints(0, max));
    }

    private void limit(String prefix, long memberId, int max, Duration window) {
        RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key(prefix, String.valueOf(memberId)), max, window);
        if (!r.allowed()) {
            throw new RateLimitedException(r.retryAfterSeconds());
        }
    }
}
