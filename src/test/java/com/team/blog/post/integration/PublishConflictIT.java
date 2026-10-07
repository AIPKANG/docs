package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 005 T515: 다른 곳에서 먼저 저장했으면 발행하지 않고, 발행 중 들어온 자동 저장은 지우지 않는다(US6, FR-010, FR-016). */
class PublishConflictIT extends IntegrationTestBase {

    @Autowired
    AutosaveBuffer buffer;

    @Test
    void staleTabCannotPublish() throws Exception {
        long me = writer(members, "pubconflict");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(autosave(postId, "탭 A", "A 본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(publish(postId, "탭 B", "B 본문", 0).with(TestAuth.member(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"))
                .andExpect(jsonPath("$.server.title").value("탭 A"))
                .andExpect(jsonPath("$.server.version").value(1));
        assertThat(posts.post(postId)).containsEntry("status", "DRAFT");
        mockMvc.perform(publish(postId, "탭 A", "A 본문", 1).with(TestAuth.member(me))).andExpect(status().isOk());
        assertThat(posts.buffer(postId)).isEmpty();
    }

    @Test
    void autosaveArrivingAfterVersionCheckSurvivesPublish() throws Exception {
        long me = writer(members, "pubrace");
        long postId = posts.draft(me, "", "", 0);
        buffer.save(postId, me, 0, 0, "버퍼 v1", "", Instant.now());
        // 발행이 v1을 확인한 뒤 다른 탭이 v2를 저장한 상황: 발행 후 정리는 v1 이하만 지운다
        buffer.save(postId, me, 1, 0, "버퍼 v2", "", Instant.now());
        buffer.evictUpTo(postId, 1);
        assertThat(posts.buffer(postId)).containsEntry("version", "2");
        buffer.evictUpTo(postId, 2);
        assertThat(posts.buffer(postId)).isEmpty();
        assertThat(posts.dirty(postId)).isFalse();
    }
}
