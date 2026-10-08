package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.discovery.application.ViewProperties;
import com.team.blog.discovery.application.ViewRecorder;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 016 FR-011: Redis가 실패하면 기록을 건너뛰고 성공으로 끝난다. */
class ViewRecorderTest {

    private final ViewProperties props = new ViewProperties(Duration.ofHours(24), 1, "같은 사람은 하루에 한 번만 세요",
            List.of("bot", "HeadlessChrome"), 60, false, Duration.ofMinutes(1), Duration.ofDays(90), "0 0 5 * * *");

    @Test
    void redisFailureSkipsRecording() {
        PostReadAccess access = mock(PostReadAccess.class);
        when(access.requireReadable(any(), anyLong())).thenReturn(new PostReadAccess.ReadablePost(1, 2, "PUBLIC", false));
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenThrow(new RedisConnectionFailureException("down"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
        ViewRecorder recorder = new ViewRecorder(access, mock(StringRedisTemplate.class), limiter, props, clock,
                new com.team.blog.discovery.application.VisitorKeys(clock));
        boolean counted = recorder.record(1, new ViewRecorder.Visit(Optional.of(new CurrentUser(3, "USER")),
                Optional.empty(), "203.0.113.7", "Mozilla/5.0", false));
        assertThat(counted).isFalse();
    }
}
