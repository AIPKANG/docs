package com.team.blog.interaction.application;

import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.shared.error.CannotFollowSelfException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 팔로우·언팔로우(018, 24 §3·§4). 상태 지정형이라 같은 요청을 여러 번(동시 포함) 보내도 결과가 같고, 행이 실제로 생기거나 없어졌을 때만
 * 사건을 낸다. 판정 순서: 공통 IP 제한(429) → 로그인(401, 인증 전 허용) → 대상(없거나 탈퇴 유예 404) → 자기 자신(400).
 */
@Service
public class FollowService {

    public record FollowResult(boolean following, long followerCount) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AccountGuard accountGuard;
    private final BlogOwnerResolver ownerResolver;
    private final RedisRateLimiter rateLimiter;
    private final FollowProperties properties;
    private final FollowQuery query;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public FollowService(JdbcTemplate jdbc, TransactionTemplate tx, AccountGuard accountGuard, BlogOwnerResolver ownerResolver,
                         RedisRateLimiter rateLimiter, FollowProperties properties, FollowQuery query,
                         ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.accountGuard = accountGuard;
        this.ownerResolver = ownerResolver;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.query = query;
        this.events = events;
        this.clock = clock;
    }

    public FollowResult set(Optional<CurrentUser> current, String handle, boolean follow, String clientIp) {
        limitIp(clientIp);
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long target = ownerResolver.resolve(handle).orElseThrow(NotFoundException::new).memberId();
        if (target == user.memberId()) {
            throw new CannotFollowSelfException();
        }
        tx.executeWithoutResult(status -> {
            if (follow) {
                Instant now = clock.instant();
                boolean created = !jdbc.queryForList("""
                        INSERT INTO follow (follower_id, followee_id, created_at) VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING RETURNING follower_id
                        """, Long.class, user.memberId(), target, Timestamp.from(now)).isEmpty();
                if (created) {
                    events.publishEvent(new MemberFollowed(user.memberId(), target, now));
                }
            } else {
                boolean removed = !jdbc.queryForList(
                        "DELETE FROM follow WHERE follower_id = ? AND followee_id = ? RETURNING follower_id",
                        Long.class, user.memberId(), target).isEmpty();
                if (removed) {
                    events.publishEvent(new MemberUnfollowed(user.memberId(), target));
                }
            }
        });
        return new FollowResult(follow, query.counts(target).followers());
    }

    private void limitIp(String ip) {
        try {
            RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("follow:ip", ip == null ? "" : ip),
                    properties.perIpPerMinute(), Duration.ofMinutes(1));
            if (!r.allowed()) {
                throw new RateLimitedException(r.retryAfterSeconds());
            }
        } catch (DataAccessException e) {
            // 제한 저장소가 멈추면 열어 둔다(공통 원칙 5)
        }
    }

    /** 익명 처리 단계(24 §6, FR-018): 그 회원의 팔로우 관계를 양방향 모두 삭제. 023이 부른다. */
    public int purgeWithdrawn(long memberId) {
        return jdbc.update("DELETE FROM follow WHERE follower_id = ? OR followee_id = ?", memberId, memberId);
    }
}
