package com.team.blog.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.friend.application.FriendPurgeStep;
import com.team.blog.friend.application.FriendQuery;
import com.team.blog.friend.application.FriendQuery.Status;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 025 친구 공개(강성찬 개인 확장, docs/06 §6). */
class FriendIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-09T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FriendQuery friendQuery;

    @Autowired
    FriendPurgeStep purgeStep;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private MockHttpServletRequestBuilder friend(String handle, String action) {
        return post("/@" + handle + "/friend").param("action", action).with(csrf());
    }

    private int notifications(long receiver, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ? AND type = ?", Integer.class,
                receiver, type);
    }

    /** 친구 공개 글: 발행한 뒤 공개 범위를 FRIENDS로(친구 공개 글에는 최초 공개 시각이 없다). */
    private long friendsPost(long author, String title, Instant at) {
        long id = posts.published(author, title, "본문 " + title, 1, at);
        jdbc.update("UPDATE post SET visibility = 'FRIENDS', first_public_at = NULL WHERE id = ?", id);
        return id;
    }

    private void befriend(long a, String aHandle, long b, String bHandle) throws Exception {
        mockMvc.perform(friend(bHandle, "request").with(TestAuth.member(a))).andExpect(status().is3xxRedirection());
        mockMvc.perform(friend(aHandle, "accept").with(TestAuth.member(b))).andExpect(status().is3xxRedirection());
        assertThat(friendQuery.areFriends(a, b)).isTrue();
    }

    @Test
    void requestAcceptDeclineCancelAndUnfriendNeverNotifyTheOtherSide() throws Exception {
        clock.set(T);
        long a = member("frasker");
        long b = member("frtarget");
        mockMvc.perform(friend("frtarget", "request").with(TestAuth.member(a))).andExpect(status().is3xxRedirection());
        assertThat(friendQuery.status(a, b)).isEqualTo(Status.SENT);
        assertThat(friendQuery.status(b, a)).isEqualTo(Status.RECEIVED);
        assertThat(notifications(b, "FRIEND_REQUEST")).isEqualTo(1);
        // 같은 요청을 다시 보내도 그대로
        mockMvc.perform(friend("frtarget", "request").with(TestAuth.member(a)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendship", Integer.class)).isEqualTo(1);
        // 거절: 행이 사라지고, 받은 사람의 안 읽은 요청 알림에서도 빠지고, 요청한 사람에게는 아무것도 없다
        mockMvc.perform(friend("frasker", "remove").with(TestAuth.member(b))).andExpect(status().is3xxRedirection());
        assertThat(friendQuery.status(a, b)).isEqualTo(Status.NONE);
        assertThat(notifications(b, "FRIEND_REQUEST")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ?", Integer.class, a)).isZero();
        // 다시 요청 → 수락 → 친구, 끊기 → 상대에게 알림 없음
        befriend(a, "frasker", b, "frtarget");
        assertThat(friendQuery.status(a, b)).isEqualTo(Status.FRIENDS);
        mockMvc.perform(friend("frtarget", "remove").with(TestAuth.member(a)));
        assertThat(friendQuery.status(b, a)).isEqualTo(Status.NONE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id IN (?, ?) AND type <> 'FRIEND_REQUEST'",
                Integer.class, a, b)).isZero();
        // 자기 자신 400, 없는 회원 404, 비회원은 로그인
        mockMvc.perform(friend("frasker", "request").with(TestAuth.member(a))).andExpect(status().isBadRequest());
        mockMvc.perform(friend("nobody-here", "request").with(TestAuth.member(a))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(friend("frtarget", "request")).andReturn().getResponse().getHeader("Location")).contains("/login");
    }

    @Test
    void crossRequestsAtTheSameTimeBecomeOneFriendship() throws Exception {
        long a = member("frcrossa");
        long b = member("frcrossb");
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            boolean fromA = i % 2 == 0;
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(friend(fromA ? "frcrossb" : "frcrossa", "request")
                        .with(TestAuth.member(fromA ? a : b))).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(303);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendship", Integer.class)).isEqualTo(1);
        assertThat(friendQuery.areFriends(a, b)).isTrue();
    }

    @Test
    void friendsPostsAreReadableOnlyByFriendsAndNeverListedPublicly() throws Exception {
        clock.set(T);
        long author = member("frauthor");
        long friend = member("frfriend");
        long stranger = member("frstranger");
        befriend(author, "frauthor", friend, "frfriend");
        posts.published(author, "공개 글 하나", "본문", 1, T.minusSeconds(3600));
        long secret = friendsPost(author, "친구만 보는 비밀글", T.minusSeconds(60));
        String url = "/@frauthor/posts/" + secret;
        mockMvc.perform(get(url).with(TestAuth.member(friend))).andExpect(status().isOk());
        mockMvc.perform(get(url).with(TestAuth.member(author))).andExpect(status().isOk());
        String strangerPage = mockMvc.perform(get(url).with(TestAuth.member(stranger))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(strangerPage).contains("볼 수 없는 글이에요").doesNotContain("친구만 보는 비밀글");
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        // 권한 표(42) 친구 공개 칸: 요청 중인 사람·관리자도 404(관리자 예외 없음)
        long pending = member("frpending");
        mockMvc.perform(friend("frauthor", "request").with(TestAuth.member(pending)));
        mockMvc.perform(get(url).with(TestAuth.member(pending))).andExpect(status().isNotFound());
        long admin = member("fradmin");
        mockMvc.perform(get(url).with(TestAuth.admin(admin))).andExpect(status().isNotFound());
        // 친구가 보는 블로그: 공개 + 친구 공개(발행 시각 최신순), 다른 사람에게는 공개만
        String friendBlog = mockMvc.perform(get("/@frauthor").with(TestAuth.member(friend))).andReturn().getResponse().getContentAsString();
        assertThat(friendBlog).contains("친구만 보는 비밀글").contains("공개 글 하나");
        assertThat(friendBlog.indexOf("친구만 보는 비밀글")).isLessThan(friendBlog.indexOf("공개 글 하나"));
        assertThat(mockMvc.perform(get("/api/members/frauthor/posts").with(TestAuth.member(friend))).andReturn().getResponse()
                .getContentAsString()).contains("친구만 보는 비밀글");
        for (String path : new String[] {"/@frauthor", "/api/members/frauthor/posts"}) {
            assertThat(mockMvc.perform(get(path).with(TestAuth.member(stranger))).andReturn().getResponse().getContentAsString())
                    .as(path).contains("공개 글 하나").doesNotContain("친구만 보는 비밀글");
        }
        // 공용 목록(홈·트렌딩·검색·피드)에는 친구에게도 나오지 않는다
        jdbc.update("INSERT INTO follow (follower_id, followee_id, created_at) VALUES (?, ?, now())", friend, author);
        for (String path : new String[] {"/", "/?tab=trending", "/api/posts", "/feed", "/search?q=" + "비밀글"}) {
            assertThat(mockMvc.perform(get(path).with(TestAuth.member(friend))).andReturn().getResponse().getContentAsString())
                    .as(path).doesNotContain("친구만 보는 비밀글");
        }
        // 친구 반응은 공개 글과 같은 규칙, 친구 아님은 404
        mockMvc.perform(put("/api/posts/{id}/like", secret).with(csrf()).with(TestAuth.member(friend)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/posts/{id}/like", secret).with(csrf()).with(TestAuth.member(stranger)))
                .andExpect(status().isNotFound());
        // 끊으면 즉시 404, 블로그에서도 빠진다
        mockMvc.perform(friend("frauthor", "remove").with(TestAuth.member(friend)));
        mockMvc.perform(get(url).with(TestAuth.member(friend))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(get("/@frauthor").with(TestAuth.member(friend))).andReturn().getResponse()
                .getContentAsString()).doesNotContain("친구만 보는 비밀글");
    }

    @Test
    void authorCanChooseFriendsVisibilityAndDefault() throws Exception {
        long author = member("frchooser");
        long id = posts.published(author, "범위 바꿀 글", "본문", 1, T);
        mockMvc.perform(patch("/api/posts/{id}/visibility", id).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"FRIENDS\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT visibility FROM post WHERE id = ?", String.class, id)).isEqualTo("FRIENDS");
        mockMvc.perform(patch("/api/me/settings").with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"defaultVisibility\":\"FRIENDS\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT default_visibility FROM member WHERE id = ?", String.class, author)).isEqualTo("FRIENDS");
    }

    @Test
    void friendListIsOwnerOnlyAndWithdrawalPurgesRelations() throws Exception {
        long a = member("frlista");
        long b = member("frlistb");
        long c = member("frlistc");
        befriend(a, "frlista", b, "frlistb");
        mockMvc.perform(friend("frlista", "request").with(TestAuth.member(c)));
        String page = mockMvc.perform(get("/settings/friends").with(TestAuth.member(a))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("내 친구 1").contains("받은 친구 요청 1").contains("@frlistb").contains("@frlistc");
        assertThat(mockMvc.perform(get("/settings/friends")).andReturn().getResponse().getHeader("Location")).contains("/login");
        purgeStep.purge(a);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendship WHERE ? IN (member_a_id, member_b_id)", Integer.class, a))
                .isZero();
    }

    @Test
    void dailyRequestLimit() throws Exception {
        long a = member("frspammer");
        for (int i = 0; i < 50; i++) {
            member("frs" + i);
            mockMvc.perform(friend("frs" + i, "request").with(TestAuth.member(a))).andExpect(status().is3xxRedirection());
        }
        member("frsextra");
        mockMvc.perform(friend("frsextra", "request").with(TestAuth.member(a))).andExpect(status().isTooManyRequests());
    }
}
