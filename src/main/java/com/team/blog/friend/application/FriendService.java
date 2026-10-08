package com.team.blog.friend.application;

import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.friend.application.FriendQuery.Status;
import com.team.blog.shared.error.CannotFriendSelfException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.event.FriendRequestClosed;
import com.team.blog.shared.event.FriendRequested;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 친구 요청·수락·거절·취소·끊기(025, 06 §6-2). 한 쌍에 행 하나라 같은 요청·맞요청을 동시에 여러 번 보내도 결과가 같다.
 * 거절·취소·끊기는 행을 지울 뿐 상대에게 알리지 않는다. 판정 순서: 로그인(401) → 대상(없거나 탈퇴 유예 404) → 자기 자신(400).
 */
@Service
public class FriendService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AccountGuard accountGuard;
    private final BlogOwnerResolver ownerResolver;
    private final RedisRateLimiter rateLimiter;
    private final FriendProperties properties;
    private final FriendQuery query;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public FriendService(JdbcTemplate jdbc, TransactionTemplate tx, AccountGuard accountGuard, BlogOwnerResolver ownerResolver,
                         RedisRateLimiter rateLimiter, FriendProperties properties, FriendQuery query,
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

    /**
     * 친구 요청. 관계가 없으면 요청을 만들고, 상대가 이미 나에게 요청했으면 바로 친구가 된다(맞요청). 이미 요청 중·친구면 그대로.
     *
     * @return 요청 뒤 내 기준 상태
     */
    public Status request(Optional<CurrentUser> current, String handle) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long me = user.memberId();
        long target = target(handle, me);
        Status before = query.status(me, target);
        if (before == Status.NONE) {
            limitDaily(me);
        }
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            long a = Math.min(me, target);
            long b = Math.max(me, target);
            boolean created = !jdbc.queryForList("""
                    INSERT INTO friendship (member_a_id, member_b_id, requested_by, status, created_at) VALUES (?, ?, ?, 'PENDING', ?)
                    ON CONFLICT DO NOTHING RETURNING member_a_id
                    """, Long.class, a, b, me, Timestamp.from(now)).isEmpty();
            if (created) {
                events.publishEvent(new FriendRequested(me, target, now));
                return;
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT status, requested_by FROM friendship WHERE member_a_id = ? AND member_b_id = ? FOR UPDATE", a, b);
            if (rows.isEmpty()) {
                return;
            }
            Map<String, Object> row = rows.get(0);
            if ("PENDING".equals(row.get("status")) && ((Number) row.get("requested_by")).longValue() == target) {
                accept(a, b, target, me, now);
            }
        });
        return query.status(me, target);
    }

    /** 받은 요청 수락. 받은 요청이 없으면 아무것도 바꾸지 않는다. */
    public Status accept(Optional<CurrentUser> current, String handle) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long me = user.memberId();
        long target = target(handle, me);
        tx.executeWithoutResult(status -> {
            long a = Math.min(me, target);
            long b = Math.max(me, target);
            List<Long> pending = jdbc.queryForList("""
                    SELECT requested_by FROM friendship WHERE member_a_id = ? AND member_b_id = ? AND status = 'PENDING'
                    FOR UPDATE
                    """, Long.class, a, b);
            if (!pending.isEmpty() && pending.get(0) == target) {
                accept(a, b, target, me, clock.instant());
            }
        });
        return query.status(me, target);
    }

    /** 거절·요청 취소·친구 끊기: 관계를 지운다. 상대에게 알리지 않는다(06 §6-2). */
    public Status remove(Optional<CurrentUser> current, String handle) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long me = user.memberId();
        long target = target(handle, me);
        tx.executeWithoutResult(status -> {
            List<Map<String, Object>> removed = jdbc.queryForList("""
                    DELETE FROM friendship WHERE member_a_id = ? AND member_b_id = ? RETURNING status, requested_by
                    """, Math.min(me, target), Math.max(me, target));
            if (!removed.isEmpty() && "PENDING".equals(removed.get(0).get("status"))) {
                long requester = ((Number) removed.get(0).get("requested_by")).longValue();
                events.publishEvent(new FriendRequestClosed(requester, requester == me ? target : me));
            }
        });
        return Status.NONE;
    }

    /** 익명 처리 단계(13 §3-3): 그 회원의 친구 관계·요청을 모두 지운다. */
    public int purgeWithdrawn(long memberId) {
        return jdbc.update("DELETE FROM friendship WHERE member_a_id = ? OR member_b_id = ?", memberId, memberId);
    }

    private void accept(long a, long b, long requester, long receiver, Instant now) {
        jdbc.update("UPDATE friendship SET status = 'ACCEPTED', accepted_at = ? WHERE member_a_id = ? AND member_b_id = ?",
                Timestamp.from(now), a, b);
        events.publishEvent(new FriendRequestClosed(requester, receiver));
    }

    private long target(String handle, long me) {
        long target = ownerResolver.resolve(handle).orElseThrow(NotFoundException::new).memberId();
        if (target == me) {
            throw new CannotFriendSelfException();
        }
        return target;
    }

    private void limitDaily(long me) {
        try {
            RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("friend:request", String.valueOf(me)),
                    properties.dailyRequestLimit(), Duration.ofDays(1));
            if (!r.allowed()) {
                throw new RateLimitedException(r.retryAfterSeconds());
            }
        } catch (DataAccessException e) {
            // 제한 저장소가 멈추면 열어 둔다(공통 원칙 5)
        }
    }
}
