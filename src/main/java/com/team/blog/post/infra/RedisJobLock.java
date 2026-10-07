package com.team.blog.post.infra;

import com.team.blog.post.application.JobLock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis {@code SET key token NX PX} 실행 잠금. 풀 때는 토큰이 같을 때만 지운다(research R-4). */
@Component
public class RedisJobLock implements JobLock {

    private static final Logger log = LoggerFactory.getLogger(RedisJobLock.class);

    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisJobLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean runExclusively(String name, Duration ttl, Runnable task) {
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(name, token, ttl);
        } catch (DataAccessException e) {
            log.warn("job lock {} unavailable: {}", name, e.getMessage());
            return false;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            try {
                redis.execute(RELEASE, List.of(name), token);
            } catch (DataAccessException e) {
                log.warn("job lock {} release failed (expires by ttl): {}", name, e.getMessage());
            }
        }
    }
}
