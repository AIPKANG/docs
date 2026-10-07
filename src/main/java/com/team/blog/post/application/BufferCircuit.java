package com.team.blog.post.application;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 간단한 차단기(research R-6): 버퍼(Redis) 접근이 실패하면 {@code blog.post.autosave.redis-retry-after} 동안
 * 자동 저장을 Redis 없이 DB 경로로 바로 처리한다. 시간이 지나면 다시 Redis를 시도한다.
 */
@Component
public class BufferCircuit {

    private final Clock clock;
    private final PostProperties properties;
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.MIN);

    public BufferCircuit(Clock clock, PostProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public boolean allowsRedis() {
        return !clock.instant().isBefore(openUntil.get());
    }

    public void recordFailure() {
        openUntil.set(clock.instant().plus(properties.autosave().redisRetryAfter()));
    }

    /** 테스트·운영 확인용: 즉시 닫는다. */
    public void reset() {
        openUntil.set(Instant.MIN);
    }
}
