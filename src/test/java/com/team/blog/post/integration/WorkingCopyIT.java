package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.AutosaveFlusher;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 004 T329: 발행 글은 작업본에만 저장하고 독자는 마지막 발행본을 본다(FR-022~FR-024, SC-005). */
class WorkingCopyIT extends IntegrationTestBase {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    AutosaveFlusher flusher;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void savesGoToWorkingCopyAndPublishedVersionStays() throws Exception {
        long me = writer(members, "republish");
        long postId = posts.published(me, "발행 제목", "발행 본문", 5, PUBLISHED_AT);

        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.title").value("발행 제목"))
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.editing").value(false));

        mockMvc.perform(autosave(postId, "고친 제목", "고친 본문", 5).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(6));
        flusher.flushBatch(500);
        assertThat(posts.workingCopyRow(postId)).containsEntry("title", "고친 제목").containsEntry("edit_version", 6L);
        Map<String, Object> post = posts.post(postId);
        assertThat(post).containsEntry("title", "발행 제목").containsEntry("content_md", "발행 본문")
                .containsEntry("content_html", "<p>발행본</p>").containsEntry("edit_version", 5L);

        mockMvc.perform(manualSave(postId, "또 고침", "", 6).with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.workingCopyRow(postId)).containsEntry("title", "또 고침").containsEntry("edit_version", 7L);
        assertThat(posts.post(postId)).containsEntry("title", "발행 제목");

        // 버퍼·브라우저 데이터가 모두 사라져도 작업본이 열린다
        redis.delete("autosave:post:" + postId);
        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.title").value("또 고침"))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.editing").value(true));
        String editor = mockMvc.perform(get("/write/{id}", postId).with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(editor).contains("discard-working-copy").contains("발행한 글을 고치는 중이에요");
    }

    @Test
    void bufferOnlyEditShowsAsEditingInList() throws Exception {
        long me = writer(members, "bufonly");
        long postId = posts.published(me, "발행 제목", "", 2, PUBLISHED_AT);
        mockMvc.perform(autosave(postId, "버퍼에만", "", 2).with(TestAuth.member(me))).andExpect(status().isOk());
        flusher.flushBatch(500); // 011: 내 글 관리의 "수정 중"은 작업본 행 기준(41 §5)
        String html = mockMvc.perform(get("/manage/posts").param("tab", "published").with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("수정 중");
    }

    @Test
    void discardRemovesWorkingCopyAndBufferAndKeepsVersionMonotonic() throws Exception {
        long me = writer(members, "discarder");
        long postId = posts.published(me, "발행 제목", "발행 본문", 5, PUBLISHED_AT);
        mockMvc.perform(manualSave(postId, "작업본", "", 5).with(TestAuth.member(me))).andExpect(status().isOk());
        posts.resetRateLimit(me);
        mockMvc.perform(autosave(postId, "버퍼 최신", "", 6).with(TestAuth.member(me))).andExpect(status().isOk());
        // 반영 작업이 버전 7을 읽어 둔 상황을 흉내 내려고 Hash를 남겨 둔 복사본을 만든다
        Map<Object, Object> staleHash = posts.buffer(postId);

        mockMvc.perform(delete("/api/posts/{id}/working-copy", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNoContent());
        assertThat(posts.workingCopyRow(postId)).isNull();
        assertThat(posts.buffer(postId)).isEmpty();
        assertThat(posts.dirty(postId)).isFalse();
        assertThat(posts.post(postId)).containsEntry("title", "발행 제목").containsEntry("edit_version", 7L);

        // 취소 뒤 옛 버퍼가 반영돼도 작업본이 되살아나지 않는다
        redis.opsForHash().putAll("autosave:post:" + postId, staleHash);
        redis.opsForSet().add("autosave:dirty", String.valueOf(postId));
        flusher.flushBatch(500);
        assertThat(posts.workingCopyRow(postId)).isNull();

        // 다시 열면 발행본, 이어서 고치면 버전 8부터
        redis.delete("autosave:post:" + postId);
        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.title").value("발행 제목")).andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.editing").value(false));
        mockMvc.perform(manualSave(postId, "다시", "", 7).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.version").value(8));

        // 작업본이 없어도 204(멱등)
        mockMvc.perform(delete("/api/posts/{id}/working-copy", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/posts/{id}/working-copy", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNoContent());
    }

    @Test
    void discardOnDraftIs404() throws Exception {
        long me = writer(members, "draftdisc");
        long postId = posts.draft(me, "임시", "", 1);
        mockMvc.perform(delete("/api/posts/{id}/working-copy", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNotFound());
        assertThat(posts.exists(postId)).isTrue();
    }
}
