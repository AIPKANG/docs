package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.publishBody;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/** 005 T512: 연타·재전송은 한 번만(US3, FR-019, SC-004). */
class PublishIdempotencyIT extends IntegrationTestBase {

    @Autowired
    StringRedisTemplate redis;

    @Test
    void twentyConcurrentRequestsWithSameKeyPublishOnce() throws Exception {
        long me = writer(members, "idemuser");
        long postId = posts.draft(me, "", "", 5);
        String key = "3f9c2a00-0000-4000-8000-000000000001";
        String body = publishBody("한 번만", "본문", "[\"jpa\"]", "PUBLIC", 5);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(publish(postId, key, body).with(TestAuth.member(me))).andReturn().getResponse();
            }));
        }
        start.countDown();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> f : futures) {
            responses.add(f.get());
        }
        pool.shutdown();
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(6L);
        assertThat(responses).allMatch(r -> r.getStatus() == 200 || r.getStatus() == 409);
        assertThat(responses).filteredOn(r -> r.getStatus() == 409)
                .allMatch(r -> contentOf(r).contains("IN_PROGRESS"));
        // 처리 중이던 요청도 같은 키로 다시 보내면 같은 성공 응답을 받는다(추가 발행 없음)
        String success = mockMvc.perform(publish(postId, key, body).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(responses).filteredOn(r -> r.getStatus() == 200).allMatch(r -> contentOf(r).equals(success));
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(6L);
    }

    private static String contentOf(MockHttpServletResponse r) {
        try {
            return r.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void inProgressReusedAndCompletedKeys() throws Exception {
        long me = writer(members, "idemstates");
        long postId = posts.draft(me, "", "", 0);
        String key = "key-in-progress";
        String body = publishBody("제목", "본문", "[]", "PUBLIC", 0);
        redis.opsForValue().set("idem:publish:" + me + ":" + key, "P|" + com.team.blog.post.application.PublishIdempotency.hash(
                postId, com.team.blog.post.web.PostPublishTestAccess.command(body)));
        mockMvc.perform(publish(postId, key, body).with(TestAuth.member(me)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IN_PROGRESS"));
        mockMvc.perform(publish(postId, key, publishBody("다른 내용", "본문", "[]", "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(posts.post(postId)).containsEntry("status", "DRAFT");

        String done = "key-done";
        mockMvc.perform(publish(postId, done, body).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(publish(postId, done, body).with(TestAuth.member(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(1L);
        assertThat(redis.getExpire("idem:publish:" + me + ":" + done)).isBetween(1L, 600L);
    }

    @Test
    void failedPublishCanBeRetriedWithSameKey() throws Exception {
        long me = writer(members, "idemretry");
        long postId = posts.draft(me, "", "", 0);
        String key = "key-retry";
        mockMvc.perform(publish(postId, key, publishBody("", "본문", "[]", "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest());
        assertThat(redis.hasKey("idem:publish:" + me + ":" + key)).isFalse();
        mockMvc.perform(publish(postId, key, publishBody("제목", "본문", "[]", "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrMalformedKeyIsRejected() throws Exception {
        long me = writer(members, "idemkey");
        long postId = posts.draft(me, "", "", 0);
        String body = publishBody("제목", "본문", "[]", "PUBLIC", 0);
        mockMvc.perform(publish(postId, null, body).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(publish(postId, "bad key!", body).with(TestAuth.member(me))).andExpect(status().isBadRequest());
        assertThat(posts.post(postId)).containsEntry("status", "DRAFT");
    }

    @Test
    void secondPublishWithNewKeyAndOldVersionConflicts() throws Exception {
        long me = writer(members, "twokeys");
        long postId = posts.draft(me, "", "", 0);
        String body = publishBody("제목", "본문", "[]", "PUBLIC", 0);
        mockMvc.perform(publish(postId, "key-a", body).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(publish(postId, "key-b", body).with(TestAuth.member(me)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EDIT_CONFLICT"))
                .andExpect(jsonPath("$.server.version").value(1));
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(1L);
    }
}
