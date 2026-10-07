package com.team.blog.account.infra;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창 요청 제한. {@code INCR} + 첫 증가 시 {@code PEXPIRE}를 Lua 스크립트 하나로 원자 처리한다.
 * 키 원문에 이메일·토큰을 넣지 않는다(001 redis-keys 규칙). 001-auth가 같은 클래스를 그대로 쓴다.
 */
@Component
public class RedisRateLimiter {

    static final String SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> REDIS_SCRIPT = RedisScript.of(SCRIPT, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 판정 결과. {@code retryAfterSeconds}는 창이 끝날 때까지 남은 초(올림, 최소 1). */
    public record Result(boolean allowed, long count, long retryAfterSeconds) {
    }

    /** 키를 1 증가시키고 한도 안이면 허용. */
    public Result tryAcquire(String key, int limit, Duration window) {
        long windowMillis = window.toMillis();
        @SuppressWarnings("unchecked")
        List<Object> raw = redis.execute(REDIS_SCRIPT, List.of(key), String.valueOf(windowMillis));
        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("rate limit script returned no result");
        }
        long count = ((Number) raw.get(0)).longValue();
        long ttlMillis = ((Number) raw.get(1)).longValue();
        return decide(count, limit, ttlMillis);
    }

    /** 한도 판정(순수 함수). */
    public static Result decide(long count, int limit, long ttlMillis) {
        long retryAfter = Math.max(1, (ttlMillis + 999) / 1000);
        return new Result(count <= limit, count, retryAfter);
    }

    /**
     * 키 계산: 각 부분을 {@code :}로 잇는다. 예) {@code key("account:handle-check:ip", ip)}.
     * 이메일·토큰 원문이 들어가지 않게 {@code @}가 든 부분은 거부한다.
     */
    public static String key(String prefix, String id) {
        if (prefix == null || prefix.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("rate limit key parts must not be blank");
        }
        if (id.indexOf('@') >= 0 || prefix.indexOf('@') >= 0) {
            throw new IllegalArgumentException("rate limit key must not contain raw email");
        }
        return prefix + ":" + id;
    }
}
