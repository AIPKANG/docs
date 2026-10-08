package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.moderation.application.ReportCleanupJob;
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
import org.springframework.test.web.servlet.ResultActions;

/** 022 신고·숨김·정지. */
class ModerationIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReportCleanupJob cleanupJob;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private long admin(String handle) {
        long id = member(handle);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", id);
        return id;
    }

    private ResultActions report(long who, String type, long id, String reason, String detail) throws Exception {
        String body = "{\"targetType\":\"" + type + "\",\"targetId\":" + id + ",\"reason\":\"" + reason + "\""
                + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mockMvc.perform(post("/api/reports").with(csrf()).with(TestAuth.member(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long caseOf(String column, long id) {
        return jdbc.queryForObject("SELECT id FROM report_case WHERE " + column + " = ? ORDER BY id DESC LIMIT 1", Long.class, id);
    }

    private long comment(long post, long author, String content) {
        long id = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, post, author, content);
        jdbc.update("UPDATE post SET comment_count = comment_count + 1 WHERE id = ?", post);
        return id;
    }

    @Test
    void reportRulesSnapshotAndIdempotency() throws Exception {
        long author = member("rpauthor");
        long a = member("rpa");
        long unverified = members.localMember("rpunver", "rpunver", "rpunver@example.com", "Blog#2026ok", false);
        String longBody = "가".repeat(2500);
        long p = posts.published(author, "신고될 글", longBody, 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        String body = "{\"targetType\":\"POST\",\"targetId\":" + p + ",\"reason\":\"SPAM\"}";
        mockMvc.perform(post("/api/reports").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        report(unverified, "POST", p, "SPAM", null).andExpect(status().isForbidden());
        String hiddenBody = report(a, "POST", priv, "SPAM", null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missingBody = report(a, "POST", 999999, "SPAM", null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(hiddenBody).isEqualTo(missingBody);
        report(author, "POST", p, "SPAM", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN"));
        report(a, "POST", p, "NOPE", null).andExpect(status().isBadRequest());
        report(a, "POST", p, "OTHER", null).andExpect(status().isBadRequest());
        report(a, "POST", p, "SPAM", "x".repeat(201)).andExpect(status().isBadRequest());
        report(a, "POST", p, "SPAM", null).andExpect(status().isCreated());
        report(a, "POST", p, "ABUSE", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report", Integer.class)).isEqualTo(1);
        var snap = jdbc.queryForMap("SELECT * FROM report_case WHERE post_id = ?", p);
        assertThat(snap.get("snapshot_title")).isEqualTo("신고될 글");
        assertThat((String) snap.get("snapshot_content")).hasSize(2000);
        assertThat(snap.get("target_author_id")).isEqualTo(author);
        // 동시에 여러 사람이 처음 신고해도 대기 묶음은 하나
        List<Long> reporters = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            reporters.add(member("rpmany" + i));
        }
        long q = posts.published(author, "동시 신고", "본문", 1, T);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (long r : reporters) {
            fs.add(pool.submit(() -> {
                start.await();
                return report(r, "POST", q, "SPAM", null).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : fs) {
            assertThat(f.get()).isEqualTo(201);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_case WHERE post_id = ?", Integer.class, q)).isEqualTo(1);
        // 1분 5건
        long busy = member("rpbusy");
        for (int i = 0; i < 5; i++) {
            long t = posts.published(author, "제한" + i, "본문", 1, T);
            report(busy, "POST", t, "SPAM", null).andExpect(status().isCreated());
        }
        long sixth = posts.published(author, "제한6", "본문", 1, T);
        report(busy, "POST", sixth, "SPAM", null).andExpect(status().isTooManyRequests());
        // 자동으로 숨기지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post WHERE hidden_at IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    void adminScreensOnlyForAdmins() throws Exception {
        long m = member("adm1");
        long unverified = members.localMember("admunv", "admunv", "admunv@example.com", "Blog#2026ok", false);
        long ad = admin("admboss");
        mockMvc.perform(get("/admin/reports")).andExpect(status().isSeeOther());
        mockMvc.perform(post("/api/admin/reports/1/reject").with(csrf())).andExpect(status().isUnauthorized());
        for (long who : new long[] {m, unverified}) {
            mockMvc.perform(get("/admin/reports").with(TestAuth.member(who))).andExpect(status().isNotFound());
            mockMvc.perform(get("/admin/members").with(TestAuth.member(who))).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/admin/reports/1/reject").with(csrf()).with(TestAuth.member(who))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/admin/reports").with(TestAuth.admin(ad))).andExpect(status().isOk());
        String members = mockMvc.perform(get("/admin/members?q=adm").with(TestAuth.admin(ad))).andReturn().getResponse().getContentAsString();
        assertThat(members).contains("@adm1").contains("관리자");
        assertThat(mockMvc.perform(get("/").with(TestAuth.admin(ad))).andReturn().getResponse().getContentAsString())
                .contains("href=\"/admin/reports\"");
        assertThat(mockMvc.perform(get("/").with(TestAuth.member(m))).andReturn().getResponse().getContentAsString())
                .doesNotContain("href=\"/admin/reports\"");
    }

    @Test
    void hidePostClosesAllReportsNotifiesAndHidesEverywhereUntilUnhidden() throws Exception {
        clock.set(T.plusSeconds(3600));
        long author = member("hdauthor");
        long r1 = member("hdr1");
        long r2 = member("hdr2");
        long ad = admin("hdadmin");
        long p = posts.published(author, "숨길 글감", "본문 숨길 글감", 1, T);
        jdbc.update("UPDATE post SET like_count = 3 WHERE id = ?", p);
        report(r1, "POST", p, "SPAM", null).andExpect(status().isCreated());
        report(r2, "POST", p, "OTHER", "설명").andExpect(status().isCreated());
        long c = caseOf("post_id", p);
        String detail = mockMvc.perform(get("/admin/reports/{id}", c).with(TestAuth.admin(ad))).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("숨길 글감").contains("신고 2건").contains("지금 상태: 공개").contains("스팸·광고").contains("설명");
        // 관리자 자신이 신고자·작성자면 처리 불가
        long adPost = posts.published(ad, "관리자 글", "본문", 1, T);
        report(r1, "POST", adPost, "SPAM", null).andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/reports/{id}/hide", caseOf("post_id", adPost)).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/reports/{id}/hide", c).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ABUSE\"}")).andExpect(status().isNoContent());
        var cs = jdbc.queryForMap("SELECT * FROM report_case WHERE id = ?", c);
        assertThat(cs.get("status")).isEqualTo("HIDDEN");
        assertThat(cs.get("handled_by")).isEqualTo(ad);
        var hiddenPost = posts.post(p);
        assertThat(hiddenPost.get("hidden_by")).isEqualTo(ad);
        assertThat(hiddenPost.get("hidden_reason")).isEqualTo("ABUSE");
        // 알림: 신고자마다 처리 결과, 작성자에게 숨김 1건(행동한 사람 없음)
        assertThat(jdbc.queryForList("SELECT receiver_id FROM notification WHERE type = 'REPORT_RESOLVED' AND result = 'ACTION_TAKEN' ORDER BY receiver_id", Long.class))
                .containsExactly(r1, r2);
        assertThat(jdbc.queryForMap("SELECT * FROM notification WHERE type = 'CONTENT_HIDDEN'"))
                .containsEntry("receiver_id", author).containsEntry("last_actor_id", null).containsEntry("post_id", p);
        String r1Notes = mockMvc.perform(get("/api/notifications").with(TestAuth.member(r1))).andReturn().getResponse().getContentAsString();
        assertThat(r1Notes).contains("신고하신 내용을 검토해 조치했어요").doesNotContain("숨길 글감").doesNotContain("hdauthor");
        String authorNotes = mockMvc.perform(get("/api/notifications").with(TestAuth.member(author))).andReturn().getResponse().getContentAsString();
        assertThat(authorNotes).contains("회원님의 글「숨길 글감」이(가) 운영 정책에 따라 숨겨졌어요").doesNotContain("hdr1").doesNotContain("hdadmin");
        // 작성자 외에는 어디에도 없음
        String url = "/@hdauthor/posts/" + p;
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(TestAuth.member(r1))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(TestAuth.admin(ad))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString()).doesNotContain("숨길 글감");
        assertThat(mockMvc.perform(get("/@hdauthor")).andReturn().getResponse().getContentAsString()).doesNotContain("숨길 글감").contains("공개 글 0");
        assertThat(mockMvc.perform(get("/api/search/posts?q=숨길글감")).andReturn().getResponse().getContentAsString()).doesNotContain("숨길 글감");
        String own = mockMvc.perform(get(url).with(TestAuth.member(author))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(own).contains("운영 정책에 따라 숨겨진 글이에요 (사유: 욕설·혐오). 다른 사람에게는 보이지 않아요");
        // 작성자가 공개 범위를 바꿔도 숨김은 그대로
        mockMvc.perform(patch("/api/posts/{id}/visibility", p).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/posts/{id}/visibility", p).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}")).andExpect(status().isOk());
        assertThat(posts.post(p).get("hidden_at")).isNotNull();
        // 해제: 원래대로, 알림 없음
        long before = jdbc.queryForObject("SELECT count(*) FROM notification", Long.class);
        String done = mockMvc.perform(get("/admin/reports?tab=done").with(TestAuth.admin(ad))).andReturn().getResponse().getContentAsString();
        assertThat(done).contains("숨김 해제");
        mockMvc.perform(post("/admin/reports/{id}/unhide", c).with(csrf()).with(TestAuth.admin(ad))).andExpect(status().isSeeOther());
        mockMvc.perform(get(url)).andExpect(status().isOk());
        assertThat(posts.post(p).get("like_count")).isEqualTo(3);
        assertThat(posts.post(p).get("hidden_by")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(before);
        // 이미 처리된 신고는 다시 처리할 수 없음
        mockMvc.perform(post("/api/admin/reports/{id}/reject", c).with(csrf()).with(TestAuth.admin(ad))).andExpect(status().isBadRequest());
    }

    @Test
    void hideAndRejectCommentsAndCleanup() throws Exception {
        long author = member("hcauthor");
        long writer = member("hcwriter");
        long r1 = member("hcr1");
        long ad = admin("hcadmin");
        long p = posts.published(author, "댓글 글", "본문", 1, T);
        long cm = comment(p, writer, "나쁜 댓글");
        long reply = jdbc.queryForObject("INSERT INTO comment (post_id, author_id, parent_id, content) VALUES (?, ?, ?, '답글') RETURNING id",
                Long.class, p, author, cm);
        jdbc.update("UPDATE post SET comment_count = comment_count + 1 WHERE id = ?", p);
        jdbc.update("INSERT INTO notification (receiver_id, type, post_id, comment_id, last_actor_id, actor_count) VALUES (?, 'COMMENT', ?, ?, ?, 1)",
                author, p, cm, writer);
        report(r1, "COMMENT", cm, "ABUSE", null).andExpect(status().isCreated());
        mockMvc.perform(post("/admin/reports/{id}/hide", caseOf("comment_id", cm)).with(csrf()).with(TestAuth.admin(ad))
                .param("reason", "ABUSE")).andExpect(status().isSeeOther());
        assertThat(posts.post(p).get("comment_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE type = 'COMMENT' AND comment_id = ?", Integer.class, cm)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM notification WHERE type = 'CONTENT_HIDDEN'")).containsEntry("receiver_id", writer)
                .containsEntry("comment_id", cm);
        String page = mockMvc.perform(get("/@hcauthor/posts/" + p).with(TestAuth.member(author))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("운영 정책에 따라 숨겨진 댓글이에요").doesNotContain("나쁜 댓글").contains("답글");
        String mine = mockMvc.perform(get("/@hcauthor/posts/" + p).with(TestAuth.member(writer))).andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("나쁜 댓글").contains("숨겨졌어요 (나만 보여요)");
        mockMvc.perform(post("/api/admin/reports/{id}/unhide", caseOf("comment_id", cm)).with(csrf()).with(TestAuth.admin(ad)))
                .andExpect(status().isNoContent());
        assertThat(posts.post(p).get("comment_count")).isEqualTo(2);
        // 문제없음
        long other = posts.published(author, "멀쩡한 글", "본문", 1, T);
        report(r1, "POST", other, "SPAM", null).andExpect(status().isCreated());
        mockMvc.perform(post("/admin/reports/{id}/reject", caseOf("post_id", other)).with(csrf()).with(TestAuth.admin(ad)))
                .andExpect(status().isSeeOther());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ? AND result = 'NO_VIOLATION'", Integer.class, r1)).isEqualTo(1);
        assertThat(posts.post(other).get("hidden_at")).isNull();
        // 대상이 완전 삭제되면 "대상 없음", 알림 없음, 30일 뒤 복사본 삭제
        long gone = posts.published(author, "지워질 글", "본문", 1, T);
        report(r1, "POST", gone, "SPAM", null).andExpect(status().isCreated());
        long goneCase = caseOf("post_id", gone);
        jdbc.update("DELETE FROM post WHERE id = ?", gone);
        long notes = jdbc.queryForObject("SELECT count(*) FROM notification", Long.class);
        cleanupJob.run();
        assertThat(jdbc.queryForObject("SELECT status FROM report_case WHERE id = ?", String.class, goneCase)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(notes);
        clock.advance(java.time.Duration.ofDays(31));
        cleanupJob.run();
        assertThat(jdbc.queryForObject("SELECT snapshot_title FROM report_case WHERE id = ?", String.class, goneCase)).isNull();
        assertThat(reply).isPositive();
    }

    @Test
    void suspendAndLift() throws Exception {
        long m = member("spuser");
        long other = admin("spadmin2");
        long ad = admin("spadmin");
        mockMvc.perform(post("/api/admin/members/{id}/suspension", m).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"7\",\"reason\":\"\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/members/{id}/suspension", m).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"3\",\"reason\":\"도배\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/members/{id}/suspension", other).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"7\",\"reason\":\"도배\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/members/{id}/suspend", m).with(csrf()).with(TestAuth.admin(ad))
                .param("period", "7").param("reason", "도배")).andExpect(status().isSeeOther());
        var s = jdbc.queryForMap("SELECT * FROM member_suspension WHERE member_id = ?", m);
        assertThat(s.get("reason")).isEqualTo("도배");
        assertThat(s.get("ends_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, m)).isEqualTo("SUSPENDED");
        assertThat(mockMvc.perform(get("/admin/members?q=spuser").with(TestAuth.admin(ad))).andReturn().getResponse().getContentAsString())
                .contains("정지 ~").contains("정지 해제");
        mockMvc.perform(delete("/api/admin/members/{id}/suspension", m).with(csrf()).with(TestAuth.admin(ad))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, m)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT lifted_by FROM member_suspension WHERE member_id = ?", Long.class, m)).isEqualTo(ad);
        mockMvc.perform(post("/api/admin/members/{id}/suspension", m).with(csrf()).with(TestAuth.admin(ad))
                .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"PERMANENT\",\"reason\":\"반복\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_suspension WHERE member_id = ? AND ends_at IS NULL AND lifted_at IS NULL",
                Integer.class, m)).isEqualTo(1);
        // 정지 회원 로그인: 기한·사유 안내(001)
        String login = mockMvc.perform(post("/login").with(csrf()).param("email", "spuser@example.com").param("password", "Blog#2026ok"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(String.valueOf(login)).doesNotEndWith("/");
    }
}
