package com.team.blog.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.friend.application.FriendPurgeStep;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 030 그룹 공개(강성찬 개인 확장): 고른 그룹에 든 친구만 읽고, 끊으면 그룹에서도 빠진다. */
class GroupIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-09T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FriendPurgeStep purge;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private void befriend(long a, String aHandle, long b, String bHandle) throws Exception {
        mockMvc.perform(post("/@" + bHandle + "/friend").param("action", "request").with(csrf()).with(TestAuth.member(a)));
        mockMvc.perform(post("/@" + aHandle + "/friend").param("action", "accept").with(csrf()).with(TestAuth.member(b)));
    }

    @Test
    void onlyChosenGroupMembersReadGroupPosts() throws Exception {
        long owner = member("growner");
        long inGroup = member("grin");
        long friendOnly = member("grfriend");
        long stranger = member("grstranger");
        befriend(owner, "growner", inGroup, "grin");
        befriend(owner, "growner", friendOnly, "grfriend");
        mockMvc.perform(post("/settings/groups").param("name", "대학 친구").with(csrf()).with(TestAuth.member(owner)))
                .andExpect(status().is3xxRedirection());
        long group = jdbc.queryForObject("SELECT id FROM friend_group WHERE owner_id = ?", Long.class, owner);
        mockMvc.perform(post("/settings/groups/{g}/members", group).param("memberId", String.valueOf(inGroup)).with(csrf())
                .with(TestAuth.member(owner))).andExpect(status().is3xxRedirection());
        // 친구가 아닌 사람은 넣을 수 없다
        mockMvc.perform(post("/settings/groups/{g}/members", group).param("memberId", String.valueOf(stranger)).with(csrf())
                .with(TestAuth.member(owner))).andExpect(status().isNotFound());
        long postId = posts.published(owner, "그룹에만 보이는 글", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'GROUP', first_public_at = NULL WHERE id = ?", postId);
        String url = "/@growner/posts/" + postId;
        // 아직 그룹을 안 골랐으면 글쓴이만
        mockMvc.perform(get(url).with(TestAuth.member(inGroup))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(get(url).with(TestAuth.member(owner))).andReturn().getResponse().getContentAsString())
                .contains("보여 줄 그룹 저장").contains("대학 친구");
        mockMvc.perform(post("/posts/{id}/groups", postId).param("groupId", String.valueOf(group)).with(csrf())
                .with(TestAuth.member(owner))).andExpect(status().is3xxRedirection());
        mockMvc.perform(get(url).with(TestAuth.member(inGroup))).andExpect(status().isOk());
        mockMvc.perform(get(url).with(TestAuth.member(friendOnly))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(TestAuth.member(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        // 블로그: 그룹에 든 친구에게만 보이고, 공용 목록에는 없다
        assertThat(mockMvc.perform(get("/@growner").with(TestAuth.member(inGroup))).andReturn().getResponse()
                .getContentAsString()).contains("그룹에만 보이는 글");
        assertThat(mockMvc.perform(get("/api/members/growner/posts").with(TestAuth.member(inGroup))).andReturn().getResponse()
                .getContentAsString()).contains("그룹에만 보이는 글");
        assertThat(mockMvc.perform(get("/@growner").with(TestAuth.member(friendOnly))).andReturn().getResponse()
                .getContentAsString()).doesNotContain("그룹에만 보이는 글");
        for (String path : new String[] {"/", "/api/posts", "/sitemap.xml"}) {
            assertThat(mockMvc.perform(get(path).with(TestAuth.member(inGroup))).andReturn().getResponse().getContentAsString())
                    .as(path).doesNotContain("그룹에만 보이는 글");
        }
        // 남의 글·남의 그룹은 못 바꾼다
        mockMvc.perform(post("/posts/{id}/groups", postId).param("groupId", String.valueOf(group)).with(csrf())
                .with(TestAuth.member(inGroup))).andExpect(status().isNotFound());
        // 친구를 끊으면 그룹에서도 빠지고 바로 못 본다
        mockMvc.perform(post("/@growner/friend").param("action", "remove").with(csrf()).with(TestAuth.member(inGroup)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM group_member WHERE group_id = ?", Integer.class, group)).isZero();
        mockMvc.perform(get(url).with(TestAuth.member(inGroup))).andExpect(status().isNotFound());
        // 탈퇴하면 그룹도 정리
        purge.purge(owner);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friend_group WHERE owner_id = ?", Integer.class, owner)).isZero();
    }

    @Test
    void groupPageIsOwnerOnlyAndNamesAreChecked() throws Exception {
        long owner = member("grpage");
        assertThat(mockMvc.perform(get("/settings/groups").with(TestAuth.member(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).contains("아직 그룹이 없어요");
        assertThat(mockMvc.perform(get("/settings/groups")).andReturn().getResponse().getHeader("Location")).contains("/login");
        mockMvc.perform(post("/settings/groups").param("name", "   ").with(csrf()).with(TestAuth.member(owner)))
                .andExpect(status().isBadRequest());
    }
}
