package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimiterIT extends IntegrationTestBase {

    @Autowired
    RedisRateLimiter limiter;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void firstIncrementSetsTtlWithinWindow() {
        limiter.tryAcquire("test:rl:a", 3, Duration.ofMinutes(1));
        Long ttl = redis.getExpire("test:rl:a");
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(60);
    }

    @Test
    void deniesAfterLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("test:rl:b", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RedisRateLimiter.Result denied = limiter.tryAcquire("test:rl:b", 3, Duration.ofMinutes(1));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.count()).isEqualTo(4);
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void concurrentIncrementsAreAtomic() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> limiter.tryAcquire("test:rl:c", 30, Duration.ofMinutes(1)).allowed());
            }
            long allowed = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(30);
            assertThat(redis.opsForValue().get("test:rl:c")).isEqualTo("50");
            assertThat(redis.getExpire("test:rl:c")).isPositive().isLessThanOrEqualTo(60);
        } finally {
            pool.shutdownNow();
        }
    }
}
