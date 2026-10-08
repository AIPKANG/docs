package com.team.blog.account.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.WithdrawalPurgeJob;
import com.team.blog.notification.application.NotificationType;
import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 023 회원 탈퇴: 신청·유예·복구·30일 뒤 정리. */
class WithdrawalIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WithdrawalPurgeJob purgeJob;

    @Autowired
    NotificationWriter notificationWriter;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private ResultActions withdraw(long who, boolean confirm, String verification) throws Exception {
        String body = "{\"confirm\":" + confirm + (verification == null ? "" : ",\"verification\":\"" + verification + "\"") + "}";
        return mockMvc.perform(post("/api/me/withdrawal").with(csrf()).with(TestAuth.member(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void screenAndChecksBeforeWithdrawing() throws Exception {
        clock.set(T);
        long me = member("wdme");
        long other = member("wdother");
        long p = posts.published(me, "내 글", "본문", 1, T);
        posts.trash(posts.published(me, "휴지통 글", "본문", 1, T));
        jdbc.update("UPDATE post SET like_count = 4 WHERE id = ?", p);
        long op = posts.published(other, "남의 글", "본문", 1, T);
        jdbc.update("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '내 댓글')", op, me);
        mockMvc.perform(get("/settings/withdraw")).andExpect(status().isSeeOther());
        String page = mockMvc.perform(get("/settings/withdraw").with(TestAuth.member(me))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("블로그 주소 @wdme").contains("내 글 2개(휴지통 포함), 남의 글에 쓴 댓글 1개, 받은 좋아요 4개")
                .contains("2026년 10월 31일").contains("현재 비밀번호").contains("id=\"withdraw-submit\" disabled");
        withdraw(me, false, "Blog#2026ok").andExpect(status().isBadRequest());
        withdraw(me, true, null).andExpect(status().isBadRequest());
        // 위의 빈 값까지 5번째 실패에서 15분 잠금
        for (int i = 0; i < 4; i++) {
            withdraw(me, true, "wrong-password").andExpect(status().isBadRequest());
        }
        withdraw(me, true, "Blog#2026ok").andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, me)).isEqualTo("ACTIVE");
        long admin = member("wdadmin");
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", admin);
        mockMvc.perform(post("/api/me/withdrawal").with(csrf()).with(TestAuth.admin(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":true,\"verification\":\"Blog#2026ok\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("관리자 권한을 해제한 뒤 탈퇴할 수 있어요"));
        // 소셜 가입은 "탈퇴" 입력, 인증 전도 허용
        long social = members.localMember("wdsocial", "wdsocial", "wdsocial@example.com", "Blog#2026ok", false);
        jdbc.update("DELETE FROM auth_identity WHERE member_id = ?", social);
        members.addSocialIdentity(social, "GOOGLE", "g-1", "wdsocial@gmail.com", T);
        assertThat(mockMvc.perform(get("/settings/withdraw").with(TestAuth.member(social))).andReturn().getResponse().getContentAsString())
                .contains("\"탈퇴\"를 입력해 주세요");
        withdraw(social, true, "탈퇴하기").andExpect(status().isBadRequest());
        withdraw(social, true, "탈퇴").andExpect(status().isOk());
    }

    @Test
    void withdrawingHidesEverythingAndRestoreBringsItBack() throws Exception {
        clock.set(T);
        long me = member("wdgone");
        long reader = member("wdreader");
        long p = posts.published(me, "사라질 글", "본문", 1, T);
        long op = posts.published(reader, "읽는 사람 글", "본문", 1, T);
        jdbc.update("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '내가 쓴 댓글')", op, me);
        jdbc.update("UPDATE post SET comment_count = 1, like_count = 1 WHERE id = ?", op);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", op, me);
        mockMvc.perform(post("/settings/withdraw").with(csrf()).with(TestAuth.member(me))
                .param("confirm", "on").param("verification", "Blog#2026ok"))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/withdrawn"));
        var row = jdbc.queryForMap("SELECT status, withdrawn_at FROM member WHERE id = ?", me);
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(((Timestamp) row.get("withdrawn_at")).toInstant()).isEqualTo(T);
        assertThat(mailpit.awaitMessagesTo("wdgone@example.com", 1).getFirst().body()).contains("2026년 10월 31일");
        mockMvc.perform(get("/@wdgone")).andExpect(status().isNotFound());
        mockMvc.perform(get("/@wdgone/posts/" + p).with(TestAuth.member(reader))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString()).doesNotContain("사라질 글");
        String opPage = mockMvc.perform(get("/@wdreader/posts/" + op)).andReturn().getResponse().getContentAsString();
        assertThat(opPage).contains("탈퇴한 사용자의 댓글이에요").doesNotContain("내가 쓴 댓글");
        assertThat(posts.post(op)).containsEntry("comment_count", 1).containsEntry("like_count", 1);
        // 유예 중 새 알림 없음
        notificationWriter.single(me, NotificationType.COMMENT, p, null, reader);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ?", Integer.class, me)).isZero();
        // 같은 이메일 가입은 안내
        String signup = mockMvc.perform(post("/signup").with(csrf()).param("email", "wdgone@example.com").param("handle", "wdgone2")
                .param("password", "Blog#2026ok").param("passwordConfirm", "Blog#2026ok").param("nickname", "다시왔다")
                .param("agreeTerms", "true").param("agreePrivacy", "true")).andReturn().getResponse().getContentAsString();
        assertThat(signup).contains("탈퇴 신청한 계정이 있어요");
        // 복구: 로그인하면 복구 전용 세션, [복구하기]를 눌러야 복구
        clock.set(T.plus(Duration.ofDays(10)));
        com.team.blog.support.Browser browser = new com.team.blog.support.Browser(mockMvc);
        assertThat(browser.perform(post("/login").with(csrf()).param("email", "wdgone@example.com").param("password", "Blog#2026ok"))
                .getResponse().getHeader("Location")).isEqualTo("/account/restore");
        assertThat(browser.perform(get("/account/restore")).getResponse().getContentAsString())
                .contains("2026년 10월 31일").contains("20일 남음").contains("복구하기");
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, me)).isEqualTo("WITHDRAWN");
        assertThat(browser.perform(get("/manage/posts")).getResponse().getHeader("Location")).isEqualTo("/account/restore");
        var restored = browser.perform(post("/account/restore").with(csrf()));
        assertThat(restored.getResponse().getHeader("Location")).isEqualTo("/");
        assertThat(browser.perform(get("/")).getResponse().getContentAsString()).contains("다시 오신 걸 환영해요");
        assertThat(browser.perform(get("/manage/posts")).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForMap("SELECT status, withdrawn_at FROM member WHERE id = ?", me))
                .containsEntry("status", "ACTIVE").containsEntry("withdrawn_at", null);
        mockMvc.perform(get("/@wdgone/posts/" + p)).andExpect(status().isOk());
        assertThat(mockMvc.perform(get("/@wdreader/posts/" + op)).andReturn().getResponse().getContentAsString()).contains("내가 쓴 댓글");
        assertThat(mailpit.awaitMessagesTo("wdgone@example.com", 2)).hasSize(2);
    }

    @Test
    void purgeAfterThirtyDaysAnonymizesAndKeepsCountsTrue() throws Exception {
        clock.set(T);
        long me = member("wdpurge");
        long a = member("wdpa");
        long b = member("wdpb");
        long mine = posts.published(me, "내 글", "본문", 1, T);
        jdbc.update("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '남이 내 글에')", mine, a);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", mine, a);
        long aPost = posts.published(a, "A의 글", "본문", 1, T);
        long top = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '답글 달린 내 댓글') RETURNING id",
                Long.class, aPost, me);
        jdbc.update("INSERT INTO comment (post_id, author_id, parent_id, content) VALUES (?, ?, ?, 'B의 답글')", aPost, b, top);
        long lonely = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '외로운 내 댓글') RETURNING id",
                Long.class, aPost, me);
        long bTop = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, 'B 최상위') RETURNING id",
                Long.class, aPost, b);
        jdbc.update("INSERT INTO comment (post_id, author_id, parent_id, content) VALUES (?, ?, ?, '내 답글')", aPost, me, bTop);
        jdbc.update("UPDATE post SET comment_count = 5, like_count = 1 WHERE id = ?", aPost);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", aPost, me);
        jdbc.update("INSERT INTO follow (follower_id, followee_id) VALUES (?, ?), (?, ?)", me, a, b, me);
        jdbc.update("INSERT INTO member_agreement (member_id, type) VALUES (?, 'AI')", me);
        jdbc.update("UPDATE member SET bio = '소개' WHERE id = ?", me);
        notificationWriter.addToGroup(a, NotificationType.LIKE, NotificationWriter.likeGroup(aPost), aPost, me);
        notificationWriter.addToGroup(a, NotificationType.LIKE, NotificationWriter.likeGroup(aPost), aPost, b);
        jdbc.update("INSERT INTO notification (receiver_id, type, result, actor_count) VALUES (?, 'REPORT_RESOLVED', 'NO_VIOLATION', 0)", me);
        jdbc.update("""
                INSERT INTO report_case (target_type, comment_id, target_author_id, snapshot_content) VALUES ('COMMENT', ?, ?, 'x')
                """, lonely, me);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?", Timestamp.from(T), me);
        // 29일: 아직
        clock.set(T.plus(Duration.ofDays(29)));
        assertThat(purgeJob.run()).isZero();
        clock.set(T.plus(Duration.ofDays(31)));
        assertThat(purgeJob.run()).isEqualTo(1);
        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", me);
        assertThat(m.get("handle")).isEqualTo("wdpurge");
        assertThat(m.get("nickname")).isNull();
        assertThat(m.get("bio")).isNull();
        assertThat(m.get("deleted_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_identity WHERE member_id = ?", Integer.class, me)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_agreement WHERE member_id = ? AND type = 'AI'", Integer.class, me)).isZero();
        assertThat(posts.exists(mine)).isFalse();
        assertThat(jdbc.queryForMap("SELECT content, deleted_at FROM comment WHERE id = ?", top)).containsEntry("content", "");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ?", Integer.class, lonely)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE author_id = ? AND deleted_at IS NULL", Integer.class, me)).isZero();
        // 수는 실제 행과 같다: B의 답글, B 최상위 = 2
        assertThat(posts.post(aPost)).containsEntry("comment_count", 2).containsEntry("like_count", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM follow WHERE follower_id = ? OR followee_id = ?", Integer.class, me, me)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ?", Integer.class, me)).isZero();
        assertThat(jdbc.queryForMap("SELECT actor_count, last_actor_id FROM notification WHERE receiver_id = ? AND type = 'LIKE'", a))
                .containsEntry("actor_count", 1).containsEntry("last_actor_id", b);
        assertThat(jdbc.queryForObject("SELECT status FROM report_case WHERE target_author_id = ?", String.class, me)).isEqualTo("CLOSED_NO_TARGET");
        // 주소는 영구 예약, 같은 이메일은 새로 가입 가능(다른 주소)
        mockMvc.perform(get("/@wdpurge")).andExpect(status().isNotFound());
        mockMvc.perform(post("/signup").with(csrf()).param("email", "wdpurge@example.com").param("handle", "wdpurge")
                .param("password", "Blog#2026ok").param("passwordConfirm", "Blog#2026ok").param("nickname", "새사람")
                .param("agreeTerms", "true").param("agreePrivacy", "true")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/signup").with(csrf()).param("email", "wdpurge@example.com").param("handle", "wdpurge_2")
                .param("password", "Blog#2026ok").param("passwordConfirm", "Blog#2026ok").param("nickname", "새사람")
                .param("agreeTerms", "true").param("agreePrivacy", "true")).andExpect(status().isSeeOther());
        // 화면: 남은 자리는 "탈퇴한 사용자"
        String page = mockMvc.perform(get("/@wdpa/posts/" + aPost)).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("탈퇴한 사용자").doesNotContain("답글 달린 내 댓글").contains("B의 답글");
        assertThat(purgeJob.run()).isZero();
    }
}
