package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.infra.RedisRateLimiter;
import org.junit.jupiter.api.Test;

class RedisRateLimiterKeyTest {

    @Test
    void keyJoinsPrefixAndId() {
        assertThat(RedisRateLimiter.key("account:handle-check:ip", "203.0.113.7"))
                .isEqualTo("account:handle-check:ip:203.0.113.7");
    }

    @Test
    void keyRejectsRawEmailAndBlankParts() {
        assertThatThrownBy(() -> RedisRateLimiter.key("auth:x", "kim@naver.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RedisRateLimiter.key("auth:x", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RedisRateLimiter.key("", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decideAllowsUpToLimitAndRoundsRetryAfterUp() {
        assertThat(RedisRateLimiter.decide(30, 30, 59_001).allowed()).isTrue();
        RedisRateLimiter.Result over = RedisRateLimiter.decide(31, 30, 59_001);
        assertThat(over.allowed()).isFalse();
        assertThat(over.retryAfterSeconds()).isEqualTo(60);
        assertThat(RedisRateLimiter.decide(31, 30, 0).retryAfterSeconds()).isEqualTo(1);
    }
}
