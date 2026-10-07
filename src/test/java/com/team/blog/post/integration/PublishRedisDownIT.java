package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.publishBody;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 005 T512·FR-020: 요청 식별자 저장소 장애에도 잠금·버전 확인으로 중복 발행이 없다. */
class PublishRedisDownIT extends IntegrationTestBase {

    @MockitoSpyBean
    StringRedisTemplate redis;

    @Test
    void duplicateIsBlockedByVersionWhenIdempotencyStoreIsDown() throws Exception {
        long me = writer(members, "idemdown");
        long postId = posts.draft(me, "", "", 0);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = org.mockito.Mockito.spy(redis.opsForValue());
        doThrow(new RedisConnectionFailureException("down")).when(ops).setIfAbsent(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.doReturn(ops).when(redis).opsForValue();

        String body = publishBody("제목", "본문", "[]", "PUBLIC", 0);
        mockMvc.perform(publish(postId, "same-key", body).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(publish(postId, "same-key", body).with(TestAuth.member(me)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EDIT_CONFLICT"));
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(1L);
    }
}
