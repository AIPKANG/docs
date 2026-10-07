package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.PASSWORD;
import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.account.application.SessionRevoker;
import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

/** 001 T166: 재설정 후 모든 기기 로그아웃(FR-019, SC-004), 다른 회원 세션은 그대로. */
class SessionRevokerIT extends IntegrationTestBase {

    @Autowired
    RedisIndexedSessionRepository sessions;

    @Autowired
    SessionRevoker sessionRevoker;

    private Browser loggedIn(String email) throws Exception {
        Browser browser = new Browser(mockMvc);
        assertThat(browser.perform(login(email, PASSWORD)).getResponse().getHeader("Location")).isEqualTo("/");
        return browser;
    }

    private int status(Browser browser) throws Exception {
        return browser.perform(get("/signup/verify-sent")).getResponse().getStatus();
    }

    @Test
    void resetInBrowserALogsOutBrowserBButNotOtherMembers() throws Exception {
        long me = members.localMember("revokeme", "나야", "me@x.com", PASSWORD, true);
        long other = members.localMember("someoneelse", "남이", "other@x.com", PASSWORD, true);
        Browser a = loggedIn("me@x.com");
        Browser b = loggedIn("me@x.com");
        Browser stranger = loggedIn("other@x.com");
        assertThat(sessions.findByPrincipalName(String.valueOf(me))).hasSize(2);

        a.perform(post("/password/forgot").with(csrf()).param("email", "me@x.com"));
        String token = mailpit.awaitMessagesTo("me@x.com", 1).get(0).token("/password/reset").orElseThrow();
        a.perform(post("/password/reset").with(csrf()).param("token", token)
                .param("password", "Fresh#2026pw").param("passwordConfirm", "Fresh#2026pw"));

        assertThat(status(b)).isEqualTo(302); // 다음 요청에서 로그인 필요
        assertThat(status(a)).isEqualTo(302);
        assertThat(sessions.findByPrincipalName(String.valueOf(me))).isEmpty();
        assertThat(status(stranger)).isEqualTo(200);
        assertThat(sessions.findByPrincipalName(String.valueOf(other))).hasSize(1);
    }

    @Test
    void revokeAllReturnsTheNumberOfDeletedSessions() throws Exception {
        long me = members.localMember("counter", "세기", "count@x.com", PASSWORD, true);
        loggedIn("count@x.com");
        loggedIn("count@x.com");
        loggedIn("count@x.com");
        assertThat(sessionRevoker.revokeAll(me)).isEqualTo(3);
        assertThat(sessionRevoker.revokeAll(me)).isZero();
    }
}
