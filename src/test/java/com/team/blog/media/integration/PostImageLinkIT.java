package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.media.application.ImageCleanupService;
import com.team.blog.post.application.AutosaveFlusher;
import com.team.blog.support.ImageFlow;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import com.team.blog.support.TestImages;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 008 T810: 글-사진 연결·카드 썸네일·남의 사진 거부·정리(FR-010, FR-019~FR-022). */
class PostImageLinkIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AutosaveFlusher flusher;

    @Autowired
    ImageCleanupService cleanup;

    private long verified(String handle) {
        return members.localMember(handle, handle, handle + "@example.com", "Blog#2026ok", true);
    }

    private Map<String, Object> image(long id) {
        return jdbc.queryForMap("SELECT status, detached_at FROM image WHERE id = ?", id);
    }

    private List<Long> linked(long postId) {
        return jdbc.queryForList("SELECT image_id FROM post_image WHERE post_id = ? ORDER BY image_id", Long.class, postId);
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private void manualSave(long me, long postId, String content, long base) throws Exception {
        mockMvc.perform(put("/api/posts/{id}/draft", postId).with(csrf()).with(TestAuth.member(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"contentMd\":" + json(content) + ",\"baseVersion\":" + base + "}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions publish(long me, long postId, String content, long base)
            throws Exception {
        return mockMvc.perform(post("/api/posts/{id}/publish", postId).with(csrf()).with(TestAuth.member(me))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목\",\"contentMd\":" + json(content) + ",\"tags\":[],\"visibility\":\"PUBLIC\",\"baseVersion\":" + base + "}"));
    }

    @Test
    void saveLinksPhotosAndRemovingDetachesThenCleanupDeletesAfterSevenDays() throws Exception {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"));
        long me = verified("linker");
        ImageFlow flow = new ImageFlow(mockMvc);
        ImageFlow.Uploaded a = flow.uploadPostWebp(me);
        ImageFlow.Uploaded b = flow.uploadPostWebp(me);
        long postId = posts.draft(me, "", "", 0);

        manualSave(me, postId, "![](" + a.url() + ")\n\n![](" + b.url() + ")", 0);
        assertThat(linked(postId)).containsExactly(a.imageId(), b.imageId());
        assertThat(image(a.imageId())).containsEntry("status", "ATTACHED");

        manualSave(me, postId, "![](" + a.url() + ")", 1);
        assertThat(linked(postId)).containsExactly(a.imageId());
        assertThat(image(b.imageId()).get("detached_at")).isNotNull();

        clock.advance(Duration.ofDays(6));
        cleanup.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, b.imageId())).isEqualTo(1);
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        cleanup.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, b.imageId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, a.imageId())).isEqualTo(1);
    }

    @Test
    void autosaveFlushLinksTooAndAttachedTempIsNotCleaned() throws Exception {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"));
        long me = verified("flushlink");
        ImageFlow.Uploaded a = new ImageFlow(mockMvc).uploadPostWebp(me);
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(csrf()).with(TestAuth.member(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"contentMd\":" + json("![](" + a.url() + ")") + ",\"baseVersion\":0}"))
                .andExpect(status().isOk());
        flusher.flushBatch(500);
        assertThat(linked(postId)).containsExactly(a.imageId());
        clock.advance(Duration.ofDays(2));
        cleanup.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, a.imageId())).isEqualTo(1);
    }

    @Test
    void publishSetsCardThumbnailAndRejectsOthersPhotos() throws Exception {
        long me = verified("pubimg");
        long other = verified("pubimgoth");
        ImageFlow flow = new ImageFlow(mockMvc);
        ImageFlow.Uploaded mine = flow.uploadPostWebp(me);
        ImageFlow.Uploaded noThumb = flow.uploadPost(me, "image/png", TestImages.png(300, 200), null);
        ImageFlow.Uploaded theirs = flow.uploadPostWebp(other);
        long postId = posts.draft(me, "", "", 0);

        publish(me, postId, "![](" + theirs.url() + ")", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentMd"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IMAGE"));
        assertThat(linked(postId)).isEmpty();

        publish(me, postId, "본문\n\n![](" + mine.url() + ")\n\n![](" + noThumb.url() + ")", 0).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM post WHERE id = ?", String.class, postId))
                .isEqualTo(mine.thumbUrl());
        assertThat(linked(postId)).containsExactly(mine.imageId(), noThumb.imageId());

        long second = posts.draft(me, "", "", 0);
        publish(me, second, "![](" + noThumb.url() + ")", 0).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM post WHERE id = ?", String.class, second))
                .isEqualTo(noThumb.url());
    }

    @Test
    void workingCopyKeepsPublishedPhotosAndDiscardDropsWorkingOnlyPhotos() throws Exception {
        long me = verified("wcimg");
        ImageFlow flow = new ImageFlow(mockMvc);
        ImageFlow.Uploaded published = flow.uploadPostWebp(me);
        ImageFlow.Uploaded editing = flow.uploadPostWebp(me);
        long postId = posts.draft(me, "", "", 0);
        publish(me, postId, "![](" + published.url() + ")", 0).andExpect(status().isOk());

        manualSave(me, postId, "![](" + editing.url() + ")", 1);
        assertThat(linked(postId)).containsExactly(published.imageId(), editing.imageId());
        assertThat(image(published.imageId()).get("detached_at")).isNull();

        mockMvc.perform(delete("/api/posts/{id}/working-copy", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNoContent());
        assertThat(linked(postId)).containsExactly(published.imageId());
        assertThat(image(editing.imageId()).get("detached_at")).isNotNull();
        mockMvc.perform(get("/@wcimg/posts/{id}", postId)).andExpect(status().isOk());
    }
}
