package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.PASSWORD;
import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T140: 로그아웃·세션 수명·쿠키(FR-024, FR-028, FR-031, FR-032). */
class LogoutAndSessionIT extends IntegrationTestBase {

    @Autowired
    RedisIndexedSessionRepository sessions;

    private long memberId;

    @BeforeEach
    void member() {
        memberId = members.localMember("logoutuser", "로그아웃", "out@x.com", PASSWORD, true);
    }

    private static String sessionId(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
    }

    private Browser loggedIn() throws Exception {
        Browser browser = new Browser(mockMvc);
        MvcResult result = browser.perform(login("out@x.com", PASSWORD));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");
        return browser;
    }

    @Test
    void logoutWithoutCsrfIsRejected() throws Exception {
        Browser browser = loggedIn();
        assertThat(browser.perform(post("/logout")).getResponse().getStatus()).isEqualTo(403);
        assertThat(sessions.findByPrincipalName(String.valueOf(memberId))).hasSize(1);
    }

    @Test
    void logoutDeletesTheServerSessionAndLeavesAOneTimeCleanupFlashOnHome() throws Exception {
        Browser browser = loggedIn();
        String oldId = sessionId(browser.sessionCookieValue());
        assertThat(sessions.findById(oldId)).isNotNull();

        MvcResult logout = browser.perform(post("/logout").with(csrf()));
        assertThat(logout.getResponse().getStatus()).isEqualTo(303);
        assertThat(logout.getResponse().getHeader("Location")).isEqualTo("/");
        assertThat(sessions.findById(oldId)).isNull();
        assertThat(sessions.findByPrincipalName(String.valueOf(memberId))).isEmpty();
        assertThat(browser.sessionCookieValue()).isNotEqualTo(browser.sessionCookieValue() == null ? null : oldId);

        String home = browser.perform(get("/")).getResponse().getContentAsString();
        assertThat(home).contains("id=\"logout-cleanup\"").contains("data-member-id=\"" + memberId + "\"")
                .contains("/js/auth/auth-logout.js");
        // 1회용
        assertThat(browser.perform(get("/")).getResponse().getContentAsString()).doesNotContain("id=\"logout-cleanup\"");

        // 로그아웃한 브라우저의 쓰기 요청 → 로그인 필요
        MvcResult ssr = browser.perform(post("/test/write").with(csrf()));
        assertThat(ssr.getResponse().getStatus()).isEqualTo(303);
        assertThat(ssr.getResponse().getHeader("Location")).startsWith("/login?redirect=");
        assertThat(browser.perform(post("/api/test/write").with(csrf())).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void oldCookieNoLongerAuthenticatesAfterLogout() throws Exception {
        Browser browser = loggedIn();
        Browser stolen = new Browser(mockMvc);
        // 같은 쿠키를 복사해 둔 다른 탭
        jakarta.servlet.http.Cookie copy = browser.sessionCookie();
        browser.perform(post("/logout").with(csrf()));
        MvcResult after = mockMvc.perform(get("/signup/verify-sent").cookie(copy)).andReturn();
        assertThat(after.getResponse().getStatus()).isEqualTo(302);
        assertThat(stolen.sessionCookieValue()).isNull();
    }

    @Test
    void sessionCookieIsHttpOnlySecureLaxAndLastsFourteenDays() throws Exception {
        MvcResult result = mockMvc.perform(login("out@x.com", PASSWORD)).andReturn();
        String setCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSION=")).reduce((a, b) -> b).orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
                .contains("Max-Age=1209600").contains("Path=/");
    }

    @Test
    void serverSessionLivesFourteenDaysAndIsIndexedByMemberId() throws Exception {
        Browser browser = loggedIn();
        String id = sessionId(browser.sessionCookieValue());
        assertThat(sessions.findById(id).getMaxInactiveInterval()).isEqualTo(Duration.ofDays(14));
        assertThat(sessions.findByPrincipalName(String.valueOf(memberId))).containsKey(id);
    }

    @Test
    void cookieIsWrittenAgainOnceADayToKeepItAlive() throws Exception {
        Browser browser = loggedIn();
        browser.perform(get("/")); // 로그인 뒤 첫 요청: 갱신 시각 기록
        MvcResult sameDay = browser.perform(get("/"));
        assertThat(sameDay.getResponse().getHeaders("Set-Cookie")).noneMatch(h -> h.startsWith("SESSION="));

        clock.advance(Duration.ofHours(25));
        MvcResult nextDay = browser.perform(get("/"));
        assertThat(nextDay.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.startsWith("SESSION=")
                && h.contains("Max-Age=1209600"));
        assertThat(sessionId(nextDay.getResponse().getCookie("SESSION").getValue()))
                .isEqualTo(sessionId(browser.sessionCookieValue()));
    }
}
