package com.team.blog.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 026 공개 전환(강성찬 개인 확장): 처음 공개되면 홈 맨 위, 친구에게 응원 알림(새 글 알림과 겹치지 않음). */
class FirstPublicCheerIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-09T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private void change(long author, long postId, String to) throws Exception {
        mockMvc.perform(patch("/api/posts/{id}/visibility", postId).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + to + "\"}")).andExpect(status().isOk());
    }

    private int count(long receiver, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ? AND type = ?", Integer.class,
                receiver, type);
    }

    @Test
    void friendsPostGoingPublicCheersFriendsOnceAndJumpsToTheTop() throws Exception {
        long author = member("fpauthor");
        long friend = member("fpfriend");
        long fan = member("fpfan");
        mockMvc.perform(post("/@fpauthor/friend").param("action", "request").with(csrf()).with(TestAuth.member(friend)));
        mockMvc.perform(post("/@fpfriend/friend").param("action", "accept").with(csrf()).with(TestAuth.member(author)));
        for (long follower : new long[] {friend, fan}) {
            jdbc.update("INSERT INTO follow (follower_id, followee_id, created_at) VALUES (?, ?, ?)", follower, author,
                    Timestamp.from(T));
        }
        posts.published(author, "예전부터 공개된 글", "본문", 1, T.minusSeconds(3600));
        long old = posts.published(author, "한 달 전에 쓴 친구 글", "본문", 1, T.minusSeconds(30L * 24 * 3600));
        jdbc.update("UPDATE post SET visibility = 'FRIENDS', first_public_at = NULL WHERE id = ?", old);
        clock.set(T);
        change(author, old, "PUBLIC");
        // 친구: 응원 1건(새 글 알림 없음), 팔로워만인 사람: 새 글 1건
        assertThat(count(friend, "FIRST_PUBLIC")).isEqualTo(1);
        assertThat(count(friend, "NEW_POST")).isZero();
        assertThat(count(fan, "NEW_POST")).isEqualTo(1);
        assertThat(count(fan, "FIRST_PUBLIC")).isZero();
        String list = mockMvc.perform(get("/api/notifications").with(TestAuth.member(friend))).andReturn().getResponse()
                .getContentAsString();
        assertThat(list).contains("친구에게만 보여 주던").contains("한 달 전에 쓴 친구 글");
        // 처음 공개된 순간이 최초 공개 시각 → 홈 맨 위
        String home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(home.indexOf("한 달 전에 쓴 친구 글")).isLessThan(home.indexOf("예전부터 공개된 글"));
        // 다시 숨겼다가 공개해도 알림은 더 없다
        change(author, old, "PRIVATE");
        change(author, old, "PUBLIC");
        assertThat(count(friend, "FIRST_PUBLIC")).isEqualTo(1);
        assertThat(count(fan, "NEW_POST")).isEqualTo(1);
    }

    @Test
    void privatePostGoingPublicHasNoCheer() throws Exception {
        long author = member("fpprivate");
        long friend = member("fpprivfriend");
        mockMvc.perform(post("/@fpprivate/friend").param("action", "request").with(csrf()).with(TestAuth.member(friend)));
        mockMvc.perform(post("/@fpprivfriend/friend").param("action", "accept").with(csrf()).with(TestAuth.member(author)));
        long id = posts.published(author, "나만 보던 글", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', first_public_at = NULL WHERE id = ?", id);
        change(author, id, "PUBLIC");
        assertThat(count(friend, "FIRST_PUBLIC")).isZero();
    }
}
