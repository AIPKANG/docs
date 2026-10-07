package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.PASSWORD;
import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.account.infra.KeyHashing;
import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MailpitClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 001 T165: 비밀번호 찾기·재설정(FR-016~FR-018, SC-003, SC-006, SC-007). */
class PasswordResetIT extends IntegrationTestBase {

    private static final String SENT = "가입된 이메일이면 안내 메일을 보냈어요";
    private static final String NEW_PASSWORD = "Fresh#2026pw";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private MockHttpServletRequestBuilder forgot(String email, String ip) {
        return post("/password/forgot").with(csrf()).param("email", email).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private String forgotBody(String email) throws Exception {
        return mockMvc.perform(forgot(email, "127.0.0.1")).andReturn().getResponse().getContentAsString();
    }

    private String resetToken(String email, int expected) {
        List<MailpitClient.Mail> mails = mailpit.awaitMessagesTo(email, expected);
        assertThat(mails).hasSize(expected);
        return mails.get(expected - 1).token("/password/reset").orElseThrow();
    }

    private MvcResult submit(String token, String password) throws Exception {
        return mockMvc.perform(post("/password/reset").with(csrf()).param("token", token)
                .param("password", password).param("passwordConfirm", password)).andReturn();
    }

    private static String strip(String html) {
        return html.replaceAll("name=\"_csrf\" content=\"[^\"]*\"", "").replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "");
    }

    @Test
    void responseIsIdenticalForRegisteredAndUnknownEmails() throws Exception {
        members.localMember("resetme", "재설정", "reset@x.com", PASSWORD, true);
        String registered = forgotBody("reset@x.com");
        String unknown = forgotBody("nobody@x.com");
        assertThat(registered).contains(SENT);
        assertThat(strip(registered)).isEqualTo(strip(unknown));
        assertThat(mailpit.awaitMessagesTo("reset@x.com", 1)).hasSize(1);
        assertThat(mailpit.countAfterQuietPeriod("nobody@x.com")).isZero();
    }

    @Test
    void mailContentDependsOnTheAccountsOfThatEmail() throws Exception {
        members.localMember("onlylocal", "로컬만", "local@x.com", PASSWORD, true);
        long both = members.localMember("bothlocal", "둘다", "both@x.com", PASSWORD, true);
        long google = members.active("go-both", "구글도");
        members.addSocialIdentity(google, "GOOGLE", "g-both", "both@x.com", Instant.now());
        long googleOnly = members.active("go-only", "구글만");
        members.addSocialIdentity(googleOnly, "GOOGLE", "g-only", "gonly@x.com", Instant.now());
        assertThat(both).isPositive();

        forgotBody("local@x.com");
        forgotBody("both@x.com");
        forgotBody("gonly@x.com");

        MailpitClient.Mail localMail = mailpit.awaitMessagesTo("local@x.com", 1).get(0);
        assertThat(localMail.token("/password/reset")).isPresent();
        assertThat(localMail.body()).doesNotContain("로 로그인하세요");

        MailpitClient.Mail bothMail = mailpit.awaitMessagesTo("both@x.com", 1).get(0);
        assertThat(bothMail.token("/password/reset")).isPresent();
        assertThat(bothMail.body()).contains("그 계정은 Google로 로그인하세요");

        MailpitClient.Mail googleMail = mailpit.awaitMessagesTo("gonly@x.com", 1).get(0);
        assertThat(googleMail.token("/password/reset")).isEmpty();
        assertThat(googleMail.body()).contains("이 이메일은 Google로 가입되어 비밀번호가 없어요");
    }

    @Test
    void requestsAreLimitedPerEmailAndPerIpWithTheSameScreen() throws Exception {
        members.localMember("limited", "제한", "limit@x.com", PASSWORD, true);
        assertThat(forgotBody("limit@x.com")).contains(SENT);
        assertThat(forgotBody("limit@x.com")).contains(SENT); // 1분 안 두 번째
        assertThat(mailpit.countAfterQuietPeriod("limit@x.com")).isEqualTo(1);

        String minuteKey = "auth:reset-req:email:" + KeyHashing.emailHash("limit@x.com") + ":min";
        for (int i = 2; i <= 10; i++) {
            redis.delete(minuteKey);
            forgotBody("limit@x.com");
        }
        assertThat(mailpit.awaitMessagesTo("limit@x.com", 10)).hasSize(10);
        redis.delete(minuteKey);
        assertThat(forgotBody("limit@x.com")).contains(SENT); // 하루 11번째
        assertThat(mailpit.countAfterQuietPeriod("limit@x.com")).isEqualTo(10);

        members.localMember("ipuser", "아이피", "ip@x.com", PASSWORD, true);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(forgot("someone" + i + "@x.com", "10.7.7.7"));
        }
        String blocked = mockMvc.perform(forgot("ip@x.com", "10.7.7.7")).andReturn().getResponse().getContentAsString();
        assertThat(blocked).contains(SENT);
        assertThat(mailpit.countAfterQuietPeriod("ip@x.com")).isZero();
        assertThat(redis.keys("auth:reset-req:*")).noneMatch(k -> k.contains("@"));
    }

    @Test
    void resetFlowChangesPasswordOnceAndPolicyViolationsKeepTheToken() throws Exception {
        long id = members.localMember("flow", "흐름", "flow@x.com", PASSWORD, true);
        forgotBody("flow@x.com");
        String token = resetToken("flow@x.com", 1);

        // 화면을 열어도 토큰은 소비되지 않는다
        String form = mockMvc.perform(get("/password/reset").param("token", token)).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("새 비밀번호 정하기").contains("value=\"" + token + "\"");
        assertThat(mockMvc.perform(get("/password/reset").param("token", token)).andReturn().getResponse().getContentAsString())
                .contains("새 비밀번호 정하기");

        MvcResult weak = submit(token, "Password1!");
        assertThat(weak.getResponse().getStatus()).isEqualTo(400);
        assertThat(weak.getResponse().getContentAsString()).contains("너무 흔한 비밀번호예요");
        MvcResult tooLong = submit(token, "Abcdefgh1!abcdefg");
        assertThat(tooLong.getResponse().getContentAsString()).contains("최대 16자");
        String hashBefore = jdbc.queryForObject("SELECT password_hash FROM auth_identity WHERE member_id = ?", String.class, id);

        MvcResult ok = submit(token, NEW_PASSWORD);
        assertThat(ok.getResponse().getStatus()).isEqualTo(303);
        assertThat(ok.getResponse().getHeader("Location")).isEqualTo("/login?reset");
        assertThat(mockMvc.perform(get("/login?reset")).andReturn().getResponse().getContentAsString())
                .contains("비밀번호를 바꿨어요. 다시 로그인해 주세요");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM auth_identity WHERE member_id = ?", String.class, id))
                .isNotEqualTo(hashBefore).startsWith("{bcrypt}");

        assertThat(mockMvc.perform(login("flow@x.com", PASSWORD)).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/login?error");
        assertThat(mockMvc.perform(login("flow@x.com", NEW_PASSWORD)).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/");

        // 같은 링크 재사용 → 만료
        assertThat(submit(token, "Another#2026").getResponse().getContentAsString()).contains("링크가 만료됐어요");
        assertThat(mockMvc.perform(get("/password/reset").param("token", token)).andReturn().getResponse().getContentAsString())
                .contains("링크가 만료됐어요").contains("[비밀번호 찾기]");
    }

    @Test
    void linkExpiresAfterThirtyMinutesAndANewRequestInvalidatesTheOldLink() throws Exception {
        members.localMember("expire", "만료", "exp@x.com", PASSWORD, true);
        forgotBody("exp@x.com");
        String first = resetToken("exp@x.com", 1);
        String key = "auth:reset:" + KeyHashing.sha256(first);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(30 * 60L - 10, 30 * 60L);

        redis.delete("auth:reset-req:email:" + KeyHashing.emailHash("exp@x.com") + ":min");
        forgotBody("exp@x.com");
        String second = resetToken("exp@x.com", 2);
        assertThat(submit(first, NEW_PASSWORD).getResponse().getContentAsString()).contains("링크가 만료됐어요");

        redis.expire("auth:reset:" + KeyHashing.sha256(second), Duration.ofMillis(1)); // 30분 경과와 같은 상태
        Thread.sleep(20);
        assertThat(submit(second, NEW_PASSWORD).getResponse().getContentAsString()).contains("링크가 만료됐어요");
    }

    @Test
    void suspendedMemberCanStillResetAndRestoreOnlySessionCanReachTheForm() throws Exception {
        long id = members.localMember("susreset", "정지재설정", "susr@x.com", PASSWORD, true);
        members.suspend(id, "사유", clock.instant().minus(Duration.ofDays(1)), clock.instant().plus(Duration.ofDays(1)));
        forgotBody("susr@x.com");
        String token = resetToken("susr@x.com", 1);
        assertThat(submit(token, NEW_PASSWORD).getResponse().getHeader("Location")).isEqualTo("/login?reset");

        long leaving = members.withdrawing("leavereset", "탈퇴재설정", Instant.now());
        members.addLocalIdentity(leaving, "leave@x.com", PASSWORD, Instant.now());
        Browser restoreOnly = new Browser(mockMvc);
        restoreOnly.perform(login("leave@x.com", PASSWORD));
        assertThat(restoreOnly.perform(get("/password/forgot")).getResponse().getStatus()).isEqualTo(200);
    }
}
