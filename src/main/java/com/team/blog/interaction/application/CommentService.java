package com.team.blog.interaction.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostCounters;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.error.CommentHiddenException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 댓글 작성·수정·삭제(014, 21 §5·§7·§8). 구조는 1단계: 답글은 항상 최상위 아래({@code parent_id} = 최상위),
 * 답글의 답글은 대상 회원만 기록한다. 최상위 행 잠금으로 같은 스레드의 작성·삭제를 직렬화한다.
 */
@Service
public class CommentService {

    public static final String REPLY_TARGET_UNAVAILABLE = "REPLY_TARGET_UNAVAILABLE";

    private final AccountGuard accountGuard;
    private final PostReadAccess postReadAccess;
    private final PostCounters postCounters;
    private final RedisRateLimiter rateLimiter;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;
    private final CommentProperties properties;
    private final CommentQuery query;
    private final Clock clock;

    public CommentService(AccountGuard accountGuard, PostReadAccess postReadAccess, PostCounters postCounters,
                          RedisRateLimiter rateLimiter, StringRedisTemplate redis, JdbcTemplate jdbc,
                          TransactionTemplate transactionTemplate, ApplicationEventPublisher events,
                          CommentProperties properties, CommentQuery query, Clock clock) {
        this.accountGuard = accountGuard;
        this.postReadAccess = postReadAccess;
        this.postCounters = postCounters;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
        this.properties = properties;
        this.query = query;
        this.clock = clock;
    }

    private record Target(long id, long postId, long authorId, Long parentId, boolean deleted, boolean hidden,
                          boolean authorWithdrawn) {

        boolean normal() {
            return !deleted && !hidden && !authorWithdrawn;
        }

        long rootId() {
            return parentId == null ? id : parentId;
        }
    }

    private Optional<Target> target(long commentId, String lock) {
        return jdbc.query("""
                SELECT c.id, c.post_id, c.author_id, c.parent_id, c.deleted_at IS NOT NULL AS deleted,
                       c.hidden_at IS NOT NULL AS hidden, m.withdrawn_at IS NOT NULL AS withdrawn
                FROM comment c JOIN member m ON m.id = c.author_id WHERE c.id = ?
                """ + lock, (rs, n) -> new Target(rs.getLong("id"), rs.getLong("post_id"), rs.getLong("author_id"),
                        (Long) rs.getObject("parent_id"), rs.getBoolean("deleted"), rs.getBoolean("hidden"),
                        rs.getBoolean("withdrawn")), commentId).stream().findFirst();
    }

    /** 작성(FR-003 순서: 로그인 → 인증 → 요청 횟수 → 내용 → 글 → 답글 대상 → 중복). */
    public CommentView create(Optional<CurrentUser> currentUser, long postId, String rawContent, Long replyToCommentId) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        limit("comment:create:member", user.memberId(), properties.createPerMinute());
        String content = CommentContentRules.clean(rawContent);
        CommentContentRules.violation(content, properties.maxLength()).ifPresent(code -> {
            throw new PostContentException(code);
        });
        postReadAccess.requireWritableTarget(currentUser, postId);
        if (replyToCommentId != null) {
            Target t = target(replyToCommentId, "").filter(x -> x.postId() == postId).filter(Target::normal)
                    .orElseThrow(() -> new PostContentException(REPLY_TARGET_UNAVAILABLE));
            replyToCommentId = t.id();
        }
        String dedupeKey = "cmt:dedupe:" + user.memberId() + ":" + postId + ":" + hash(content + "|" + replyToCommentId);
        Optional<Long> earlier = claimOrEarlier(dedupeKey);
        if (earlier.isPresent()) {
            return query.single(currentUser, earlier.get());
        }
        Long replyTo = replyToCommentId;
        Instant now = clock.instant();
        long created;
        try {
            created = transactionTemplate.execute(status -> {
                Long rootId = null;
                Long replyToMember = null;
                if (replyTo != null) {
                    // 대상과 그 최상위를 잠근다: 동시에 최상위 삭제가 오면 한쪽이 먼저 반영된다(FR-028)
                    Target t = target(replyTo, " FOR SHARE OF c").filter(Target::normal)
                            .orElseThrow(() -> new PostContentException(REPLY_TARGET_UNAVAILABLE));
                    rootId = t.rootId();
                    if (t.parentId() != null) {
                        Target root = target(rootId, " FOR SHARE OF c")
                                .orElseThrow(() -> new PostContentException(REPLY_TARGET_UNAVAILABLE));
                        if (root.postId() != postId) {
                            throw new PostContentException(REPLY_TARGET_UNAVAILABLE);
                        }
                    }
                    if (t.parentId() != null && t.authorId() != user.memberId()) {
                        replyToMember = t.authorId();
                    }
                }
                Long id = jdbc.queryForObject("""
                        INSERT INTO comment (post_id, author_id, parent_id, reply_to_member_id, content, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """, Long.class, postId, user.memberId(), rootId, replyToMember, content, Timestamp.from(now),
                        Timestamp.from(now));
                postCounters.adjustComments(postId, +1);
                events.publishEvent(new CommentCreated(id, postId, user.memberId(), rootId, replyToMember));
                return id;
            });
        } catch (RuntimeException e) {
            clearDedupe(dedupeKey);
            throw e;
        }
        rememberDedupe(dedupeKey, created);
        return query.single(currentUser, created);
    }

    /** 수정: 본인만(남의 것·삭제된 자리 404), 숨김 409, 같은 내용이면 그대로. */
    public CommentView update(Optional<CurrentUser> currentUser, long commentId, String rawContent) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        limit("comment:edit:member", user.memberId(), properties.editPerMinute());
        Target t = target(commentId, "").filter(x -> x.authorId() == user.memberId()).filter(x -> !x.deleted())
                .orElseThrow(NotFoundException::new);
        postReadAccess.requireReadable(currentUser, t.postId());
        if (t.hidden()) {
            throw new CommentHiddenException();
        }
        String content = CommentContentRules.clean(rawContent);
        CommentContentRules.violation(content, properties.maxLength()).ifPresent(code -> {
            throw new PostContentException(code);
        });
        jdbc.update("UPDATE comment SET content = ?, updated_at = GREATEST(?, created_at + interval '1 microsecond') WHERE id = ? AND content <> ?",
                content, Timestamp.from(clock.instant()), commentId, content);
        return query.single(currentUser, commentId);
    }

    /**
     * 삭제(FR-024~FR-029): 본인만. 답글이 있는 최상위는 내용을 비운 자리로, 그 밖은 행 삭제. 자리만 남은 최상위에 답글이 없어지면
     * 자리도 지운다. 댓글 수는 숨김이 아니었을 때만 −1.
     */
    public void delete(Optional<CurrentUser> currentUser, long commentId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        Target first = target(commentId, "").filter(x -> x.authorId() == user.memberId()).filter(x -> !x.deleted())
                .orElseThrow(NotFoundException::new);
        postReadAccess.requireReadable(currentUser, first.postId());
        Boolean deleted = transactionTemplate.execute(status -> {
            // 최상위를 먼저 잠근다(작성의 FOR SHARE와 직렬화)
            target(first.rootId(), " FOR UPDATE OF c");
            Target t = target(commentId, " FOR UPDATE OF c").filter(x -> !x.deleted()).orElse(null);
            if (t == null) {
                return false;
            }
            if (t.parentId() == null) {
                Integer replies = jdbc.queryForObject("SELECT count(*) FROM comment WHERE parent_id = ?", Integer.class, t.id());
                if (replies != null && replies > 0) {
                    jdbc.update("UPDATE comment SET content = '', deleted_at = ? WHERE id = ?", Timestamp.from(clock.instant()), t.id());
                } else {
                    jdbc.update("DELETE FROM comment WHERE id = ?", t.id());
                }
            } else {
                jdbc.update("DELETE FROM comment WHERE id = ?", t.id());
                // 자리만 남은 최상위에 답글이 없으면 자리도 정리(댓글 수 변화 없음)
                jdbc.update("""
                        DELETE FROM comment r WHERE r.id = ? AND r.deleted_at IS NOT NULL
                          AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.parent_id = r.id)
                        """, t.parentId());
            }
            if (!t.hidden()) {
                postCounters.adjustComments(t.postId(), -1);
            }
            events.publishEvent(new CommentDeleted(t.id(), t.postId()));
            return true;
        });
        if (!Boolean.TRUE.equals(deleted)) {
            throw new NotFoundException();
        }
    }

    private void limit(String prefix, long memberId, int perMinute) {
        RedisRateLimiter.Result result = rateLimiter.tryAcquire(RedisRateLimiter.key(prefix, String.valueOf(memberId)),
                perMinute, Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new RateLimitedException(result.retryAfterSeconds());
        }
    }

    /**
     * 10초 중복 방지(FR-030): 처음 요청이 {@code SET NX}로 자리를 잡고("0"), 만든 뒤 댓글 번호로 바꾼다. 이미 자리가 있으면 처음 요청의
     * 댓글 번호가 나올 때까지 잠깐 기다렸다가 그 댓글을 돌려준다. 저장소 장애면 그냥 만든다.
     */
    private Optional<Long> claimOrEarlier(String key) {
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(key, "0", properties.dedupeWindow());
            if (Boolean.TRUE.equals(claimed)) {
                return Optional.empty();
            }
            for (int i = 0; i < 30; i++) {
                String value = redis.opsForValue().get(key);
                if (value == null) {
                    return Optional.empty();
                }
                if (!"0".equals(value)) {
                    long id = Long.parseLong(value);
                    Integer alive = jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ? AND deleted_at IS NULL",
                            Integer.class, id);
                    return alive != null && alive > 0 ? Optional.of(id) : Optional.empty();
                }
                Thread.sleep(50);
            }
        } catch (DataAccessException | NumberFormatException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private void rememberDedupe(String key, long id) {
        try {
            redis.opsForValue().set(key, String.valueOf(id), properties.dedupeWindow());
        } catch (DataAccessException e) {
            // 무시
        }
    }

    private void clearDedupe(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            // 무시
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
