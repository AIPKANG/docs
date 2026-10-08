package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.interaction.application.FollowService;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** 018 팔로우·팔로잉 피드. */
@RecordApplicationEvents
class FollowIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @Autowired
    FollowService followService;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private int follows() {
        return jdbc.queryForObject("SELECT count(*) FROM follow", Integer.class);
    }

    @Test
    void followIsIdempotentConcurrentAndChecksOrder() throws Exception {
        clock.set(T);
        long a = member("fwtarget");
        long b = member("fwfan");
        mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(b)))
                .andExpect(jsonPath("$.followerCount").value(1));
        assertThat(events.stream(MemberFollowed.class)).hasSize(1);
        mockMvc.perform(delete("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(b)))
                .andExpect(jsonPath("$.following").value(false)).andExpect(jsonPath("$.followerCount").value(0));
        mockMvc.perform(delete("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(b)))
                .andExpect(status().isOk());
        assertThat(events.stream(MemberUnfollowed.class)).hasSize(1);
        // 동시 20건 → 관계 1, 새 팔로워 알림 1
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        long c = member("fwrace");
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(c)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(follows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor_count FROM notification WHERE receiver_id = ? AND type = 'FOLLOW'",
                Integer.class, a)).isEqualTo(1);
        // 판정: 비회원 401, 자기 자신 400, 없는·탈퇴 유예 404, 인증 전 허용
        mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(a)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
        mockMvc.perform(put("/api/members/nobodyhere/follow").with(csrf()).with(TestAuth.member(b))).andExpect(status().isNotFound());
        members.withdrawing("fwleaving", "fwleaving", T);
        mockMvc.perform(put("/api/members/fwleaving/follow").with(csrf()).with(TestAuth.member(b))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/members/fwleaving/followers")).andExpect(status().isNotFound());
        long unverified = members.localMember("fwunver", "fwunver", "fwunver@example.com", "Blog#2026ok", false);
        mockMvc.perform(put("/api/members/fwtarget/follow").with(csrf()).with(TestAuth.member(unverified))).andExpect(status().isOk());
        assertThatDbRejectsSelfFollow(a);
    }

    private void assertThatDbRejectsSelfFollow(long a) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("INSERT INTO follow (follower_id, followee_id) VALUES (?, ?)", a, a))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void listsAndCountsExcludeWithdrawnAndPaginate() throws Exception {
        clock.set(T);
        long a = member("fwlist");
        List<Long> fans = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            long f = member("fwfan" + i);
            fans.add(f);
            jdbc.update("INSERT INTO follow (follower_id, followee_id, created_at) VALUES (?, ?, ?)", f, a,
                    Timestamp.from(T.plusSeconds(i)));
        }
        jdbc.update("UPDATE member SET bio = '첫 줄 소개\n둘째 줄' WHERE id = ?", fans.get(21));
        jdbc.update("INSERT INTO follow (follower_id, followee_id) VALUES (?, ?)", a, fans.get(21));
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?", Timestamp.from(T), fans.get(0));
        String first = mockMvc.perform(get("/api/members/fwlist/followers").with(TestAuth.member(fans.get(5))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].handle").value("fwfan21"))
                .andExpect(jsonPath("$.items[0].bio").value("첫 줄 소개"))
                .andReturn().getResponse().getContentAsString();
        String cursor = first.replaceAll("^.*\"nextCursor\":\"([^\"]+)\".*$", "$1");
        mockMvc.perform(get("/api/members/fwlist/followers?cursor=" + cursor))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].handle").value("fwfan1"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(get("/api/members/fwlist/following"))
                .andExpect(jsonPath("$.items[0].handle").value("fwfan21")).andExpect(jsonPath("$.items[0].followedByMe").value(false));
        mockMvc.perform(get("/api/members/fwlist/following").with(TestAuth.member(a)))
                .andExpect(jsonPath("$.items[0].followedByMe").value(true));
        String blog = mockMvc.perform(get("/@fwlist")).andReturn().getResponse().getContentAsString();
        assertThat(blog).contains("팔로워 21").contains("팔로잉 1").contains("/login?redirect=/@fwlist");
        String page = mockMvc.perform(get("/@fwlist/followers")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("님의 팔로워 21").contains("@fwfan21").doesNotContain("@fwfan0<").contains("더 보기");
        // 복구하면 다시 포함
        jdbc.update("UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", fans.get(0));
        assertThat(mockMvc.perform(get("/@fwlist")).andReturn().getResponse().getContentAsString()).contains("팔로워 22");
        String empty = mockMvc.perform(get("/@fwfan3/followers")).andReturn().getResponse().getContentAsString();
        assertThat(empty).contains("아직 팔로워가 없어요");
        // 화면 폼(스크립트 없음): 처리 뒤 돌아감, 바깥 주소로는 안 감
        mockMvc.perform(post("/@fwfan3/follow").with(csrf()).with(TestAuth.member(a)).param("following", "true")
                .param("back", "//evil.example")).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/@fwfan3"));
        String mine = mockMvc.perform(get("/@fwfan3").with(TestAuth.member(a))).andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("팔로잉 ✓").contains("data-following=\"true\"");
        assertThat(mockMvc.perform(get("/@fwlist").with(TestAuth.member(a))).andReturn().getResponse().getContentAsString())
                .doesNotContain("class=\"follow-button\"");
        // 알림의 내 팔로워 목록
        mockMvc.perform(get("/me/followers").with(TestAuth.member(a))).andExpect(header().string("Location", "/@fwlist/followers"));
        assertThat(followService.purgeWithdrawn(a)).isEqualTo(24);
    }

    @Test
    void feedShowsOnlyFollowedPublicPostsWithoutDuplicates() throws Exception {
        clock.set(T);
        long me = member("fdreader");
        long w1 = member("fdwriter1");
        long w2 = member("fdwriter2");
        long stranger = member("fdstranger");
        mockMvc.perform(get("/feed")).andExpect(status().isSeeOther());
        mockMvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
        assertThat(mockMvc.perform(get("/feed").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("팔로우한 사람이 없어요. 홈에서 읽고 싶은 블로그를 찾아보세요");
        jdbc.update("INSERT INTO follow (follower_id, followee_id) VALUES (?, ?), (?, ?)", me, w1, me, w2);
        assertThat(mockMvc.perform(get("/feed").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("팔로우한 사람의 공개 글이 아직 없어요");
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            expected.add(posts.published(i % 2 == 0 ? w1 : w2, "피드글" + i, "본문", 1, T.plusSeconds(i)));
        }
        posts.published(stranger, "모르는 사람", "본문", 1, T);
        long priv = posts.published(w1, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long trashed = posts.published(w1, "휴지통", "본문", 1, T);
        posts.trash(trashed);
        posts.draft(w1, "임시", "", 0);
        Set<Long> seen = new HashSet<>();
        String body = mockMvc.perform(get("/api/feed").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString();
        collect(body, seen);
        String cursor = body.replaceAll("^.*\"nextCursor\":\"([^\"]+)\".*$", "$1");
        body = mockMvc.perform(get("/api/feed?cursor=" + cursor).with(TestAuth.member(me))).andReturn().getResponse().getContentAsString();
        collect(body, seen);
        assertThat(body).doesNotContain("\"nextCursor\":\"");
        assertThat(seen).isEqualTo(expected);
        String page = mockMvc.perform(get("/feed").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("피드글11").contains("?cursor=").doesNotContain("모르는 사람");
        // 언팔로우 직후 다음 요청부터 빠짐, 작성자 탈퇴 유예도 빠짐
        mockMvc.perform(delete("/api/members/fdwriter2/follow").with(csrf()).with(TestAuth.member(me))).andExpect(status().isOk());
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?", Timestamp.from(T), w1);
        mockMvc.perform(get("/api/feed").with(TestAuth.member(me))).andExpect(jsonPath("$.items.length()").value(0));
        String header = mockMvc.perform(get("/").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString();
        assertThat(header).contains("href=\"/feed\"");
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString()).doesNotContain("href=\"/feed\"");
    }

    private static void collect(String body, Set<Long> seen) {
        Matcher m = Pattern.compile("\"url\":\"/@[a-z0-9_]+/posts/(\\d+)\"").matcher(body);
        while (m.find()) {
            assertThat(seen.add(Long.parseLong(m.group(1)))).isTrue();
        }
    }

    @Test
    void followRequestsCountTowardCommonIpLimit() throws Exception {
        long a = member("fwlimit");
        long b = member("fwlimitb");
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(put("/api/members/fwlimit/follow").with(csrf()).with(TestAuth.member(b))
                    .with(r -> { r.setRemoteAddr("198.51.100.9"); return r; })).andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/members/fwlimit/follow").with(csrf()).with(TestAuth.member(b))
                .with(r -> { r.setRemoteAddr("198.51.100.9"); return r; })).andExpect(status().isTooManyRequests());
        assertThat(a).isPositive();
    }
}
