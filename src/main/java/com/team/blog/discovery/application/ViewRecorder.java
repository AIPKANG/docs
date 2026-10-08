package com.team.blog.discovery.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 조회 기록(016, 31 §3~§5-1). 글을 볼 수 있어야 하고(404), 작성자·관리자·봇·미리 불러오기는 세지 않는다. 방문자 × 글마다
 * 기간 안에 {@code max-per-window}번까지만 Redis에 모으고(Lua로 원자), 1분마다 DB에 반영한다. Redis가 실패하면 건너뛴다.
 * 원래 IP·방문자 값은 저장·로그하지 않는다(쿠키 없는 비회원은 IP+UA+그날의 비밀값 해시).
 */
@Service
public class ViewRecorder {

    private static final Logger log = LoggerFactory.getLogger(ViewRecorder.class);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    static final String RECORD_SCRIPT = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if n <= tonumber(ARGV[2]) then
              redis.call('HINCRBY', KEYS[2], ARGV[3], 1)
              return 1
            end
            return 0
            """;
    private static final RedisScript<Long> RECORD = RedisScript.of(RECORD_SCRIPT, Long.class);

    /** 요청에서 판정에 쓰는 값만(원래 IP는 해시 입력으로만 쓰고 보관하지 않음). */
    public record Visit(Optional<CurrentUser> viewer, Optional<String> visitorCookie, String ip, String userAgent,
                        boolean prefetch) {
    }

    private final PostReadAccess postReadAccess;
    private final StringRedisTemplate redis;
    private final RedisRateLimiter rateLimiter;
    private final ViewProperties properties;
    private final Clock clock;
    private final VisitorKeys visitorKeys;

    public ViewRecorder(PostReadAccess postReadAccess, StringRedisTemplate redis, RedisRateLimiter rateLimiter,
                        ViewProperties properties, Clock clock, VisitorKeys visitorKeys) {
        this.visitorKeys = visitorKeys;
        this.postReadAccess = postReadAccess;
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return 셌으면 true(응답은 셌든 안 셌든 204) */
    public boolean record(long postId, Visit visit) {
        PostReadAccess.ReadablePost post = postReadAccess.requireReadable(visit.viewer(), postId);
        if (post.hidden()) {
            throw new com.team.blog.shared.error.NotFoundException();
        }
        if (visit.prefetch() || isBot(visit.userAgent())) {
            return false;
        }
        if (visit.viewer().isPresent()) {
            CurrentUser user = visit.viewer().get();
            if (user.memberId() == post.authorId() || "ADMIN".equals(user.role())) {
                return false;
            }
        }
        String visitor = visitorKey(visit);
        try {
            RedisRateLimiter.Result limit = rateLimiter.tryAcquire(RedisRateLimiter.key("view:visitor", visitor),
                    properties.perMinute(), Duration.ofMinutes(1));
            if (!limit.allowed()) {
                throw new RateLimitedException(limit.retryAfterSeconds());
            }
            String day = LocalDate.ofInstant(clock.instant(), SEOUL).format(DAY);
            Long counted = redis.execute(RECORD, List.of("view:seen:" + postId + ":" + visitor, "view:pending:" + day),
                    String.valueOf(properties.dedupeWindow().toMillis()), String.valueOf(properties.maxPerWindow()),
                    String.valueOf(postId));
            return counted != null && counted == 1;
        } catch (DataAccessException e) {
            log.warn("view recording skipped: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return properties.botUserAgents().stream().anyMatch(b -> ua.contains(b.toLowerCase(Locale.ROOT)));
    }

    String visitorKey(Visit visit) {
        return visitorKeys.key(visit.viewer(), visit.visitorCookie(), visit.ip(), visit.userAgent());
    }
}
