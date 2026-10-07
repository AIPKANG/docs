package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.infra.PostEditStore;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 004 T314: 수동 저장은 같은 버전 확인 후 즉시 DB에 반영한다(FR-002, FR-017, research R-5). */
class ManualSaveIT extends IntegrationTestBase {

    @MockitoSpyBean
    PostEditStore store;

    @Test
    void manualSaveWritesDatabaseImmediately() throws Exception {
        long me = writer(members, "manual");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(manualSave(postId, "바로 저장", "본문", 0).with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(posts.post(postId)).containsEntry("title", "바로 저장").containsEntry("edit_version", 1L);
        assertThat(posts.dirty(postId)).isFalse();
    }

    @Test
    void manualSaveIsNotRateLimitedButChecksVersion() throws Exception {
        long me = writer(members, "manualtwo");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(autosave(postId, "자동", "", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        // 5초 안이지만 수동 저장은 제한하지 않는다
        mockMvc.perform(manualSave(postId, "수동", "", 1).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(manualSave(postId, "수동", "", 2).with(TestAuth.member(me))).andExpect(status().isOk());
        // 옛 버전 출발 → 409, 아무것도 바뀌지 않음
        mockMvc.perform(manualSave(postId, "옛 탭", "", 1).with(TestAuth.member(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.server.title").value("수동"))
                .andExpect(jsonPath("$.server.version").value(3));
        assertThat(posts.post(postId)).containsEntry("title", "수동").containsEntry("edit_version", 3L);
    }

    @Test
    void databaseFailureAfterBufferAcceptReturnsSaveDelayedWithVersion() throws Exception {
        long me = writer(members, "delayed");
        long postId = posts.draft(me, "", "", 0);
        doThrow(new DataAccessResourceFailureException("db down")).when(store)
                .flushDraft(org.mockito.ArgumentMatchers.eq(postId), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), anyLong(), org.mockito.ArgumentMatchers.any(Instant.class));
        mockMvc.perform(manualSave(postId, "늦게 반영", "", 0).with(TestAuth.member(me)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SAVE_DELAYED"))
                .andExpect(jsonPath("$.version").value(1));
        // 버퍼에는 있고 반영 대기 중
        assertThat(posts.buffer(postId)).containsEntry("title", "늦게 반영");
        assertThat(posts.dirty(postId)).isTrue();
    }
}
