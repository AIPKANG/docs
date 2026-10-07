package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.PASSWORD;
import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T139: 로그인 시 정지·탈퇴 유예(FR-030, research R-9, 42 P-7·P-12). */
class AccountStatusOnLoginIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void currentSuspensionRefusesLoginWithEndDateAndReasonOnlyWhenPasswordIsRight() throws Exception {
        long id = members.localMember("suspended", "정지됨", "sus@x.com", PASSWORD, true);
        Instant now = clock.instant();
        members.suspend(id, "스팸 게시", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(6)));

        Browser browser = new Browser(mockMvc);
        MvcResult right = browser.perform(login("sus@x.com", PASSWORD));
        assertThat(right.getResponse().getHeader("Location")).isEqualTo("/login?suspended");
        String page = browser.perform(get("/login?suspended")).getResponse().getContentAsString();
        assertThat(page).contains("정지된 계정이에요 (~").contains("까지, 스팸 게시)");
        // 로그인되지 않음
        assertThat(browser.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(302);

        MvcResult wrong = mockMvc.perform(login("sus@x.com", "Wrong#2026x")).andReturn();
        assertThat(wrong.getResponse().getHeader("Location")).isEqualTo("/login?error");
    }

    @Test
    void permanentSuspensionSaysPermanent() throws Exception {
        long id = members.localMember("forever", "영구정지", "forever@x.com", PASSWORD, true);
        members.suspend(id, "반복 위반", clock.instant().minus(Duration.ofDays(1)), null);
        Browser browser = new Browser(mockMvc);
        browser.perform(login("forever@x.com", PASSWORD));
        assertThat(browser.perform(get("/login?suspended")).getResponse().getContentAsString())
                .contains("정지된 계정이에요 (~영구, 반복 위반)");
    }

    @Test
    void expiredSuspensionIsLiftedAutomaticallyOnLogin() throws Exception {
        long id = members.localMember("wasbanned", "정지끝", "was@x.com", PASSWORD, true);
        Instant now = clock.instant();
        long suspensionId = members.suspend(id, "기간 만료", now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(1)));
        members.setStatus(id, "SUSPENDED");

        MvcResult result = mockMvc.perform(login("was@x.com", PASSWORD)).andReturn();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");

        Map<String, Object> row = jdbc.queryForMap("SELECT lifted_at, lifted_by FROM member_suspension WHERE id = ?", suspensionId);
        assertThat(row.get("lifted_at")).isNotNull();
        assertThat(row.get("lifted_by")).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void withdrawingMemberGetsARestoreOnlySession() throws Exception {
        long id = members.withdrawing("goingaway", "떠나는중", Instant.now());
        members.addLocalIdentity(id, "away@x.com", PASSWORD, Instant.now());

        Browser browser = new Browser(mockMvc);
        MvcResult result = browser.perform(login("away@x.com", PASSWORD));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/account/restore");

        MvcResult restore = browser.perform(get("/account/restore"));
        assertThat(restore.getResponse().getStatus()).isEqualTo(200);
        assertThat(restore.getResponse().getContentAsString()).contains("복구하기").contains("로그아웃");

        for (String path : new String[] {"/", "/signup", "/@goingaway", "/api/handles/availability?handle=abc"}) {
            MvcResult other = browser.perform(get(path));
            assertThat(other.getResponse().getStatus()).as(path).isEqualTo(303);
            assertThat(other.getResponse().getHeader("Location")).as(path).isEqualTo("/account/restore");
        }
        assertThat(browser.perform(get("/js/auth/password-rules.js")).getResponse().getStatus()).isEqualTo(200);
        assertThat(browser.perform(get("/password/forgot")).getResponse().getStatus()).isNotEqualTo(303);

        MvcResult logout = browser.perform(post("/logout").with(csrf()));
        assertThat(logout.getResponse().getHeader("Location")).isEqualTo("/");
        assertThat(browser.perform(get("/signup")).getResponse().getStatus()).isEqualTo(200);
    }
}
