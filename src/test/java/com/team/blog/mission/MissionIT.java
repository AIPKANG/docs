package com.team.blog.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.mission.application.MissionPurgeStep;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 034 같은 주제로 쓰기·릴레이(강성찬 개인 확장). */
class MissionIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-09T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MissionPurgeStep purge;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    @Test
    void openJoinRelayAndEnd() throws Exception {
        clock.set(T);
        long host = member("mshost");
        long a = member("msa");
        long b = member("msb");
        String loc = mockMvc.perform(post("/missions").param("title", "올해 가장 잘한 일").param("description", "짧게라도")
                .param("days", "7").with(csrf()).with(TestAuth.member(host))).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        long mission = Long.parseLong(loc.substring(loc.lastIndexOf('/') + 1));
        long pa = posts.published(a, "A의 잘한 일", "본문", 1, T);
        long pb = posts.published(b, "B의 잘한 일", "본문", 1, T);
        long priv = posts.published(b, "B의 비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', first_public_at = NULL WHERE id = ?", priv);
        mockMvc.perform(post("/missions/join").param("missionId", String.valueOf(mission)).param("postId", String.valueOf(pa))
                .with(csrf()).with(TestAuth.member(a))).andExpect(status().is3xxRedirection());
        clock.set(T.plusSeconds(60));
        mockMvc.perform(post("/missions/{id}/join", mission).param("postId", String.valueOf(pb)).with(csrf())
                .with(TestAuth.member(b))).andExpect(status().is3xxRedirection());
        // 남의 글·비공개 글로는 참여 못 함
        mockMvc.perform(post("/missions/{id}/join", mission).param("postId", String.valueOf(pa)).with(csrf())
                .with(TestAuth.member(b))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/missions/{id}/join", mission).param("postId", String.valueOf(priv)).with(csrf())
                .with(TestAuth.member(b))).andExpect(status().isBadRequest());
        String page = mockMvc.perform(get("/missions/{id}", mission)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(page).contains("올해 가장 잘한 일").contains("참여 2명").contains("7일 남음");
        assertThat(page.indexOf("A의 잘한 일")).isLessThan(page.indexOf("B의 잘한 일"));
        assertThat(mockMvc.perform(get("/@msa/posts/" + pa)).andReturn().getResponse().getContentAsString())
                .contains("🏃 올해 가장 잘한 일");
        assertThat(mockMvc.perform(get("/@msa/posts/" + pa).with(TestAuth.member(a))).andReturn().getResponse()
                .getContentAsString()).contains("이 글로 미션에 참여");
        // 참여 글이 비공개가 되면 릴레이에서 빠진다
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", pa);
        assertThat(mockMvc.perform(get("/missions/{id}", mission)).andReturn().getResponse().getContentAsString())
                .doesNotContain("A의 잘한 일").contains("B의 잘한 일");
        // 끝나면 참여 못 함, 목록의 끝난 미션
        clock.set(T.plus(Duration.ofDays(8)));
        long pc = posts.published(host, "늦은 글", "본문", 1, T.plus(Duration.ofDays(8)));
        mockMvc.perform(post("/missions/{id}/join", mission).param("postId", String.valueOf(pc)).with(csrf())
                .with(TestAuth.member(host))).andExpect(status().isBadRequest());
        assertThat(mockMvc.perform(get("/missions")).andReturn().getResponse().getContentAsString()).contains("끝난 미션");
        purge.purge(b);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_participant WHERE member_id = ?", Integer.class, b)).isZero();
        // 기간·제목 검사
        mockMvc.perform(post("/missions").param("title", " ").param("days", "7").with(csrf()).with(TestAuth.member(host)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/missions").param("title", "너무 긴 기간").param("days", "31").with(csrf()).with(TestAuth.member(host)))
                .andExpect(status().isBadRequest());
    }
}
