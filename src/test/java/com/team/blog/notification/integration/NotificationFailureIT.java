package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 017 FR-002·SC-008: 알림 처리가 실패해도 댓글·좋아요는 성공한다. */
class NotificationFailureIT extends IntegrationTestBase {

    @MockitoSpyBean
    NotificationWriter writer;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void failingNotificationDoesNotBreakActions() throws Exception {
        Instant t = Instant.parse("2026-10-08T00:00:00Z");
        clock.set(t);
        doThrow(new IllegalStateException("boom")).when(writer).single(anyLong(), any(), anyLong(), any(), anyLong());
        doThrow(new IllegalStateException("boom")).when(writer).addToGroup(anyLong(), any(), any(), any(), anyLong());
        long a = members.localMember("ntfail", "ntfail", "ntfail@example.com", "Blog#2026ok", true);
        long b = members.localMember("ntfailb", "ntfailb", "ntfailb@example.com", "Blog#2026ok", true);
        long p = posts.published(a, "글", "본문", 1, t);
        mockMvc.perform(post("/api/posts/{id}/comments", p).with(csrf()).with(TestAuth.member(b))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}")).andExpect(status().isCreated());
        mockMvc.perform(put("/api/posts/{id}/like", p).with(csrf()).with(TestAuth.member(b))).andExpect(status().isOk());
        assertThat(posts.post(p)).containsEntry("comment_count", 1).containsEntry("like_count", 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }
}
