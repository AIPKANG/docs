package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.blog.discovery.application.TrendingProperties;
import com.team.blog.discovery.application.TrendingService;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.post.application.PostCard;
import com.team.blog.post.application.PostListQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 019 FR-012·SC-007: 순위 저장소를 못 쓰면 DB에서 바로 계산한 첫 9개, 다음 없음. */
class TrendingFallbackTest {

    @Test
    void redisFailureComputesDirectly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        PostListQuery listQuery = mock(PostListQuery.class);
        PostCard card = new PostCard(7, "/@a/posts/7", "글", "", null, Instant.EPOCH, 0, 1, new PostCard.Author("a", "에이", null));
        when(listQuery.cardsByIds(any())).thenReturn(Map.of(7L, card));
        PostAccessPolicy policy = mock(PostAccessPolicy.class);
        when(policy.publicListingCondition("p", "m")).thenReturn("TRUE");
        TrendingService service = new TrendingService(jdbc, redis, listQuery, policy,
                new TrendingProperties(Duration.ofDays(7), 3, 2, 0.1, 2, 1.5, 3, 100, 9, Duration.ofMinutes(30), false,
                        Duration.ofMinutes(10)), Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC));
        CardPage page = service.page(null);
        assertThat(page.items()).containsExactly(card);
        assertThat(page.nextCursor()).isNull();
    }
}
