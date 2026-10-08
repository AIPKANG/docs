package com.team.blog.interaction.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostCounters;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.error.CannotLikeOwnPostException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostUnliked;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 좋아요(015, 30 §4). 상태 지정 방식(토글 아님): 이미 그 상태면 성공·수 변화 없음. 판정 순서 42 §3: 로그인 → 인증 → 글 볼 수 있음(404)
 * → 자기 글(400) → 요청 횟수(429). 기록 INSERT/DELETE와 수 변경은 한 트랜잭션, 실제로 바뀐 때만 ±1과 사건.
 */
@Service
public class LikeService {

    public record LikeResult(boolean liked, int likeCount) {
    }

    private final AccountGuard accountGuard;
    private final PostReadAccess postReadAccess;
    private final PostCounters postCounters;
    private final RedisRateLimiter rateLimiter;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LikeService(AccountGuard accountGuard, PostReadAccess postReadAccess, PostCounters postCounters,
                       RedisRateLimiter rateLimiter, JdbcTemplate jdbc, TransactionTemplate transactionTemplate,
                       ApplicationEventPublisher events, Clock clock) {
        this.accountGuard = accountGuard;
        this.postReadAccess = postReadAccess;
        this.postCounters = postCounters;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
        this.clock = clock;
    }

    public LikeResult set(Optional<CurrentUser> currentUser, long postId, boolean like) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        PostReadAccess.ReadablePost post = postReadAccess.requireWritableTarget(currentUser, postId);
        if (post.authorId() == user.memberId()) {
            throw new CannotLikeOwnPostException();
        }
        RedisRateLimiter.Result limit = rateLimiter.tryAcquire(
                RedisRateLimiter.key("like:member", String.valueOf(user.memberId())), 60, Duration.ofMinutes(1));
        if (!limit.allowed()) {
            throw new RateLimitedException(limit.retryAfterSeconds());
        }
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> {
            int changed = like
                    ? jdbc.update("INSERT INTO post_like (post_id, member_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                            postId, user.memberId(), Timestamp.from(now))
                    : jdbc.update("DELETE FROM post_like WHERE post_id = ? AND member_id = ?", postId, user.memberId());
            if (changed == 1) {
                postCounters.adjustLikes(postId, like ? +1 : -1);
                events.publishEvent(like ? new PostLiked(postId, user.memberId(), post.authorId(), now)
                        : new PostUnliked(postId, user.memberId()));
            }
            return new LikeResult(like, postCounters.likeCount(postId));
        });
    }

    /** 글 상세 처음 화면: 내가 눌렀는지(PK 조회 1번). */
    public boolean likedBy(long postId, long memberId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM post_like WHERE post_id = ? AND member_id = ?",
                Integer.class, postId, memberId);
        return n != null && n > 0;
    }
}
