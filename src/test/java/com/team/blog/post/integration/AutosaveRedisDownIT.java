package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.post.application.BufferCircuit;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 004 T321: 서버 버퍼(Redis)에 닿지 못하면 DB에 바로 쓰고 같은 버전 확인을 한다(FR-013, SC-007, research R-6). */
class AutosaveRedisDownIT extends IntegrationTestBase {

    @MockitoSpyBean
    AutosaveBuffer buffer;

    @Autowired
    BufferCircuit circuit;

    private void bufferDown() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("redis down");
        doThrow(down).when(buffer).save(anyLong(), anyLong(), anyLong(), anyLong(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.any(Instant.class));
        doThrow(down).when(buffer).read(anyLong());
    }

    @AfterEach
    void closeCircuit() {
        reset(buffer);
        circuit.reset();
    }

    @Test
    void autosaveFallsBackToDatabaseWithVersionCheck() throws Exception {
        long me = writer(members, "redisdown");
        long postId = posts.draft(me, "", "", 0);
        bufferDown();

        mockMvc.perform(autosave(postId, "장애 중 저장", "본문", 0).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(posts.post(postId)).containsEntry("title", "장애 중 저장").containsEntry("edit_version", 1L);

        // 같은 출발 버전의 다른 탭 → 409(DB 내용)
        mockMvc.perform(autosave(postId, "다른 탭", "", 0).with(TestAuth.member(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.server.title").value("장애 중 저장"))
                .andExpect(jsonPath("$.server.version").value(1));

        // 회로가 열린 동안에는 Redis를 부르지 않고(요청 제한도 건너뜀) 바로 DB
        clearInvocations(buffer);
        mockMvc.perform(manualSave(postId, "수동", "", 1).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(autosave(postId, "자동", "", 2).with(TestAuth.member(me))).andExpect(status().isOk());
        verify(buffer, never()).save(anyLong(), anyLong(), anyLong(), anyLong(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.any(Instant.class));
        assertThat(posts.post(postId)).containsEntry("title", "자동").containsEntry("edit_version", 3L);
        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void workingCopyFallbackKeepsPublishedVersion() throws Exception {
        long me = writer(members, "redisdownpub");
        long postId = posts.published(me, "발행", "발행 본문", 4, Instant.parse("2026-10-01T00:00:00Z"));
        bufferDown();
        mockMvc.perform(autosave(postId, "작업본", "", 4).with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.workingCopyRow(postId)).containsEntry("title", "작업본").containsEntry("edit_version", 5L);
        assertThat(posts.post(postId)).containsEntry("title", "발행").containsEntry("edit_version", 4L);
    }

    @Test
    void afterRecoveryStaleBufferDoesNotWinOverNewerDatabaseVersion() throws Exception {
        long me = writer(members, "recovered");
        long postId = posts.draft(me, "", "", 0);
        // 장애 전 버퍼에 버전 1
        buffer.save(postId, me, 0, 0, "장애 전", "", Instant.now());
        bufferDown();
        mockMvc.perform(autosave(postId, "장애 중", "", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        // DB 경로도 버전을 확인한다: DB 버전 0 → 1
        assertThat(posts.post(postId)).containsEntry("edit_version", 1L);
        mockMvc.perform(autosave(postId, "장애 중 2", "", 1).with(TestAuth.member(me))).andExpect(status().isOk());

        // 복구: 버퍼 버전 1 < DB 버전 2 → DB가 현재 내용
        reset(buffer);
        clock.advance(Duration.ofMinutes(1));
        posts.resetRateLimit(me);
        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.title").value("장애 중 2")).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(autosave(postId, "복구 후", "", 2).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
    }
}
