package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 004 T312: 자동 저장 → 서버 버퍼(US1-3, FR-005, FR-016, FR-017, US3-1). */
class AutosaveIT extends IntegrationTestBase {

    @Test
    void autosaveStoresInBufferAndBumpsVersion() throws Exception {
        long me = writer(members, "autosaver");
        long postId = posts.draft(me, "", "", 0);

        mockMvc.perform(autosave(postId, "JPA N+1 정리", "## 문제\n쿼리가 N번", 0).with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.savedAt").exists());
        assertThat(posts.buffer(postId)).containsEntry("title", "JPA N+1 정리")
                .containsEntry("contentMd", "## 문제\n쿼리가 N번")
                .containsEntry("version", "1")
                .containsEntry("memberId", String.valueOf(me));
        assertThat(posts.dirty(postId)).isTrue();
        // DB는 반영 작업 전까지 그대로
        assertThat(posts.post(postId).get("edit_version")).isEqualTo(0L);

        posts.resetRateLimit(me);
        mockMvc.perform(autosave(postId, "JPA N+1 정리", "## 문제\n쿼리가 N번 실행", 1).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/posts/{id}/editing", postId).with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentMd").value("## 문제\n쿼리가 N번 실행"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.editing").value(false));
    }

    @Test
    void staleBaseVersionIsRejectedWithServerContent() throws Exception {
        long me = writer(members, "twotabs");
        long postId = posts.draft(me, "", "", 0);
        // 탭 A 저장
        mockMvc.perform(autosave(postId, "A의 제목", "A 본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        posts.resetRateLimit(me);
        // 탭 B는 같은 버전 0에서 출발
        mockMvc.perform(autosave(postId, "B의 제목", "B 본문", 0).with(TestAuth.member(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"))
                .andExpect(jsonPath("$.server.title").value("A의 제목"))
                .andExpect(jsonPath("$.server.contentMd").value("A 본문"))
                .andExpect(jsonPath("$.server.version").value(1))
                .andExpect(jsonPath("$.server.savedAt").exists());
        // B는 아무것도 덮어쓰지 않았다
        assertThat(posts.buffer(postId)).containsEntry("title", "A의 제목").containsEntry("version", "1");
    }

    @Test
    void conflictAgainstDatabaseVersionWhenBufferIsEmpty() throws Exception {
        long me = writer(members, "dbversion");
        long postId = posts.draft(me, "저장된 제목", "저장된 본문", 7);
        mockMvc.perform(autosave(postId, "새 제목", "", 6).with(TestAuth.member(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.server.title").value("저장된 제목"))
                .andExpect(jsonPath("$.server.version").value(7));
        posts.resetRateLimit(me);
        mockMvc.perform(autosave(postId, "새 제목", "", 7).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.version").value(8));
    }

    @Test
    void onlyTitleAndBodyAreSavedAndTitleControlCharsBecomeSpaces() throws Exception {
        long me = writer(members, "onlytitle");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(csrf()).with(TestAuth.member(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"줄\\n바꿈\",\"contentMd\":\"a\\r\\nb\",\"baseVersion\":0,\"tags\":[\"spring\"],\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        assertThat(posts.buffer(postId)).containsEntry("title", "줄 바꿈").containsEntry("contentMd", "a\nb")
                .doesNotContainKey("tags");
        assertThat(posts.post(postId).get("visibility")).isEqualTo("PUBLIC");
    }

    @Test
    void autosaveKeepsLocalImagePlaceholder() throws Exception {
        long me = writer(members, "localimg");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(autosave(postId, "사진", "![업로드 대기](local:7f3e)", 0).with(TestAuth.member(me)))
                .andExpect(status().isOk());
        assertThat(posts.buffer(postId)).containsEntry("contentMd", "![업로드 대기](local:7f3e)");
    }
}
