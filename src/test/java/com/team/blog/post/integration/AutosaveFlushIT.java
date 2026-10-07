package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.application.AutosaveFlusher;
import com.team.blog.post.application.JobLock;
import com.team.blog.post.application.PostProperties;
import com.team.blog.support.TestAuth;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 004 T313: 버퍼 → DB 영구 반영(FR-006, FR-007, SC-001). */
class AutosaveFlushIT extends IntegrationTestBase {

    @Autowired
    AutosaveFlusher flusher;

    @Autowired
    AutosaveBuffer buffer;

    @Autowired
    JobLock jobLock;

    @Autowired
    PostProperties properties;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void flushWritesDraftAndSurvivesBufferLoss() throws Exception {
        long me = writer(members, "flusher");
        long postId = posts.draft(me, "", "", 0);
        clock.set(Instant.parse("2026-10-07T05:00:00Z"));
        mockMvc.perform(autosave(postId, "제목", "본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());

        assertThat(flusher.flushBatch(500)).isEqualTo(1);
        var row = posts.post(postId);
        assertThat(row).containsEntry("title", "제목").containsEntry("content_md", "본문").containsEntry("edit_version", 1L)
                .containsEntry("content_html", "");
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toInstant()).isEqualTo(Instant.parse("2026-10-07T05:00:00Z"));
        assertThat(posts.dirty(postId)).isFalse();
        // 버퍼 Hash는 남는다(04 §2-4)
        assertThat(posts.buffer(postId)).containsEntry("version", "1");

        // 서버 임시 보관이 사라져도 다시 열면 영구 저장 내용(SC-001)
        redis.delete("autosave:post:" + postId);
        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void olderBufferVersionNeverOverwritesNewerDatabaseVersion() {
        long me = writer(members, "olderbuf");
        long postId = posts.draft(me, "DB의 더 새 내용", "", 5);
        // 장애 복구 뒤 남아 있던 옛 버퍼(버전 3)
        redis.opsForHash().putAll("autosave:post:" + postId, java.util.Map.of("memberId", String.valueOf(me),
                "title", "옛 내용", "contentMd", "", "version", "3", "savedAt", "2026-10-07T00:00:00Z"));
        redis.opsForSet().add("autosave:dirty", String.valueOf(postId));

        flusher.flushBatch(500);
        assertThat(posts.post(postId)).containsEntry("title", "DB의 더 새 내용").containsEntry("edit_version", 5L);
        assertThat(posts.dirty(postId)).isFalse();
    }

    @Test
    void newerVersionArrivingDuringFlushStaysDirty() {
        long me = writer(members, "racing");
        long postId = posts.draft(me, "", "", 0);
        buffer.save(postId, me, 0, 0, "v1", "", Instant.now());
        // 반영 작업이 v1을 읽어 DB에 쓰는 사이 v2가 들어온 상황: v1 반영 표시만 하면 dirty는 남아야 한다
        buffer.save(postId, me, 1, 0, "v2", "", Instant.now());
        buffer.markFlushed(postId, 1);
        assertThat(posts.dirty(postId)).isTrue();

        flusher.flushBatch(500);
        assertThat(posts.post(postId)).containsEntry("title", "v2").containsEntry("edit_version", 2L);
        assertThat(posts.dirty(postId)).isFalse();
    }

    @Test
    void trashedOrDeletedPostIsDroppedFromDirtyList() {
        long me = writer(members, "trashed");
        long postId = posts.draft(me, "", "", 0);
        buffer.save(postId, me, 0, 0, "내용", "", Instant.now());
        posts.trash(postId);
        flusher.flushBatch(500);
        assertThat(posts.post(postId)).containsEntry("title", "");
        assertThat(posts.dirty(postId)).isFalse();
    }

    @Test
    void jobRunsOnceWhileLockIsHeld() {
        long me = writer(members, "locked");
        long postId = posts.draft(me, "", "", 0);
        buffer.save(postId, me, 0, 0, "내용", "", Instant.now());
        AutosaveFlushJob job = new AutosaveFlushJob(flusher, jobLock, properties);

        // 다른 서버가 잠금을 잡고 있으면 건너뛴다
        redis.opsForValue().set("autosave:flush-lock", "other-server", Duration.ofSeconds(50));
        job.run();
        assertThat(posts.post(postId)).containsEntry("title", "");

        redis.delete("autosave:flush-lock");
        job.run();
        assertThat(posts.post(postId)).containsEntry("title", "내용");
        assertThat(redis.hasKey("autosave:flush-lock")).isFalse();
    }

    @Test
    void lockIsExclusive() {
        AtomicBoolean inner = new AtomicBoolean();
        boolean outer = jobLock.runExclusively("test:lock", Duration.ofSeconds(10),
                () -> inner.set(jobLock.runExclusively("test:lock", Duration.ofSeconds(10), () -> { })));
        assertThat(outer).isTrue();
        assertThat(inner.get()).isFalse();
    }
}
