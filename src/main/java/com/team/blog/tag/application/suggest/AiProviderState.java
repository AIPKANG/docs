package com.team.blog.tag.application.suggest;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 공급자 상태(34 §3-2): {@code ai:gemini:exhausted}(다음 초기화까지), {@code ai:gemini:cooldown}(60초),
 * {@code ai:gemini:count:{한도 기준 날짜}}, {@code ai:gemini:unknown:{날짜}}(종류 모를 한도 연속), {@code ai:ollama:inflight}.
 */
@Component
public class AiProviderState {

    private final StringRedisTemplate redis;
    private final AiTagProperties properties;
    private final Clock clock;

    public AiProviderState(StringRedisTemplate redis, AiTagProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    private ZoneId resetZone() {
        return ZoneId.of(properties.gemini().resetZone());
    }

    private String today() {
        return LocalDate.ofInstant(clock.instant(), resetZone()).toString();
    }

    private Duration untilReset() {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), resetZone());
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(resetZone()));
    }

    /** ①~③: 소진·일시 중단·오늘 호출 수가 한도 이상이면 쓸 수 없음(③은 소진 표시를 남긴다). */
    public boolean geminiAvailable() {
        if (Boolean.TRUE.equals(redis.hasKey("ai:gemini:exhausted")) || Boolean.TRUE.equals(redis.hasKey("ai:gemini:cooldown"))) {
            return false;
        }
        String count = redis.opsForValue().get("ai:gemini:count:" + today());
        if (count != null && Long.parseLong(count) >= properties.gemini().dailyLimit()) {
            markExhausted();
            return false;
        }
        return true;
    }

    public void markExhausted() {
        redis.opsForValue().set("ai:gemini:exhausted", "1", untilReset());
    }

    public void cooldown() {
        redis.opsForValue().set("ai:gemini:cooldown", "1", properties.cooldown());
    }

    /** 종류 모를 한도: 60초 일시 중단, 같은 날 3번 연속이면 소진. */
    public void unknownLimit() {
        cooldown();
        String key = "ai:gemini:unknown:" + today();
        Long n = redis.opsForValue().increment(key);
        redis.expire(key, untilReset().plusMinutes(1));
        if (n != null && n >= 3) {
            markExhausted();
        }
    }

    public void geminiSucceeded() {
        String key = "ai:gemini:count:" + today();
        redis.opsForValue().increment(key);
        redis.expire(key, untilReset().plusHours(1));
        redis.delete("ai:gemini:unknown:" + today());
    }

    /** Ollama 동시 처리 자리 얻기(여러 서버 공통). 못 얻으면 false. */
    public boolean acquireOllama() {
        Long n = redis.opsForValue().increment("ai:ollama:inflight");
        redis.expire("ai:ollama:inflight", properties.ollama().timeout().plusSeconds(30));
        if (n != null && n > properties.ollama().maxConcurrency()) {
            redis.opsForValue().decrement("ai:ollama:inflight");
            return false;
        }
        return true;
    }

    public void releaseOllama() {
        redis.opsForValue().decrement("ai:ollama:inflight");
    }
}
