package com.team.blog.post.application;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.event.PostVisibilityChanged;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 범위 바꾸기(006 R-4, 06 §4): 다시 발행 없이 즉시. 행 잠금 아래에서 바꾸고 {@code first_public_at}은 처음 "발행 + 공개"일 때만,
 * {@code edited_at}·작업본·{@code edit_version}은 그대로. 같은 값이면 아무것도 바꾸지 않는다. 임시글은 값만 저장한다.
 * 판정 순서 42 §3: 401/403 → 404(대상) → 400(값).
 */
@Service
public class PostVisibilityService {

    private final AccountGuard accountGuard;
    private final PostAccessPolicy accessPolicy;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PostVisibilityService(AccountGuard accountGuard, PostAccessPolicy accessPolicy, JdbcTemplate jdbc,
                                 ApplicationEventPublisher events, Clock clock) {
        this.accountGuard = accountGuard;
        this.accessPolicy = accessPolicy;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    public record Result(String visibility, Instant firstPublicAt) {
    }

    @Transactional
    public Result change(Optional<CurrentUser> currentUser, long postId, String to) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        record Locked(String visibility, Instant firstPublicAt) {
        }
        Locked current = jdbc.query("""
                SELECT visibility, first_public_at FROM post
                WHERE id = ? AND author_id = ? AND deleted_at IS NULL FOR UPDATE
                """, (rs, n) -> new Locked(rs.getString("visibility"), instant(rs.getTimestamp("first_public_at"))),
                postId, user.memberId()).stream().findFirst().orElseThrow(NotFoundException::new);
        if (to == null || !accessPolicy.supportedVisibilities().contains(to)) {
            throw new ProfileValidationException(FieldError.of("visibility", "INVALID_VISIBILITY"));
        }
        if (current.visibility().equals(to)) {
            return new Result(to, current.firstPublicAt());
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Instant firstPublicAt = jdbc.queryForObject("""
                UPDATE post SET visibility = ?,
                       first_public_at = CASE WHEN first_public_at IS NULL AND status = 'PUBLISHED' AND ? = 'PUBLIC'
                                              THEN ? ELSE first_public_at END,
                       updated_at = ?
                WHERE id = ?
                RETURNING first_public_at
                """, (rs, n) -> instant(rs.getTimestamp("first_public_at")), to, to, now, now, postId);
        events.publishEvent(new PostVisibilityChanged(postId, user.memberId(), current.visibility(), to, firstPublicAt));
        return new Result(to, firstPublicAt);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
