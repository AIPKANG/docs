package com.team.blog.account.integration;

import static com.team.blog.account.integration.SocialTestSupport.socialLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T152: 소셜 로그인 판정(FR-020~FR-022, FR-030). 공급자 응답은 SocialLoginProbeController로 재현. */
class SocialLoginIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private int memberCount() {
        return jdbc.queryForObject("SELECT count(*) FROM member", Integer.class);
    }

    @Test
    void firstLoginCreatesNoAccountAndGoesToSignupCompletion() throws Exception {
        Browser browser = new Browser(mockMvc);
        MvcResult result = browser.perform(socialLogin("GOOGLE", "g-111", "first@gmail.com", "Kim Min-seo"));
        assertThat(result.getResponse().getStatus()).isEqualTo(303);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/signup/social");
        assertThat(memberCount()).isZero();
        // 로그인되지 않았다
        assertThat(browser.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(302);
        // 대기 정보로 마무리 화면이 열린다
        assertThat(browser.perform(get("/signup/social")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void linkedAccountLogsInWithNewSessionIdEvenIfItsEmailChanged() throws Exception {
        long id = members.active("go-linked", "연결됨");
        members.addSocialIdentity(id, "GOOGLE", "g-222", "old@gmail.com", Instant.now());

        Browser browser = new Browser(mockMvc);
        browser.perform(get("/login"));
        String before = browser.sessionCookieValue();
        MvcResult result = browser.perform(socialLogin("GOOGLE", "g-222", "changed@gmail.com", "Linked"));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");
        assertThat(browser.sessionCookieValue()).isNotNull().isNotEqualTo(before);
        assertThat(browser.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(200);
        assertThat(memberCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM auth_identity WHERE member_id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void suspendedOrWithdrawingSocialMembersAreHandledLikeFormLogin() throws Exception {
        long suspended = members.active("gi-banned", "정지깃허브");
        members.addSocialIdentity(suspended, "GITHUB", "9001", "b@x.com", Instant.now());
        members.suspend(suspended, "도배", clock.instant().minus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(3)));
        Browser a = new Browser(mockMvc);
        assertThat(a.perform(socialLogin("GITHUB", "9001", "b@x.com", "banned")).getResponse().getHeader("Location"))
                .isEqualTo("/login?suspended");
        assertThat(a.perform(get("/login?suspended")).getResponse().getContentAsString()).contains("정지된 계정이에요 (~").contains("도배");
        assertThat(a.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(302);

        long leaving = members.withdrawing("go-leaving", "떠나요구글", Instant.now());
        members.addSocialIdentity(leaving, "GOOGLE", "g-333", "l@gmail.com", Instant.now());
        Browser b = new Browser(mockMvc);
        assertThat(b.perform(socialLogin("GOOGLE", "g-333", "l@gmail.com", "leaving")).getResponse().getHeader("Location"))
                .isEqualTo("/account/restore");
        assertThat(b.perform(get("/")).getResponse().getHeader("Location")).isEqualTo("/account/restore");
    }

    @Test
    void stateMismatchOrProviderErrorGoesToLoginWithSocialError() throws Exception {
        Browser browser = new Browser(mockMvc);
        MvcResult callback = browser.perform(get("/login/oauth2/code/google").param("code", "abc").param("state", "forged"));
        assertThat(callback.getResponse().getStatus()).isEqualTo(303);
        assertThat(callback.getResponse().getHeader("Location")).isEqualTo("/login?error=social");
        assertThat(browser.perform(get("/login?error=social")).getResponse().getContentAsString())
                .contains("소셜 로그인에 실패했어요. 다시 시도해 주세요");

        MvcResult denied = browser.perform(get("/login/oauth2/code/github").param("error", "access_denied").param("state", "x"));
        assertThat(denied.getResponse().getHeader("Location")).isEqualTo("/login?error=social");
        assertThat(memberCount()).isZero();
    }

    @Test
    void authorizationRequestCarriesAState() throws Exception {
        MvcResult google = mockMvc.perform(get("/oauth2/authorization/google")).andReturn();
        assertThat(google.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth").contains("state=").contains("client_id=test-google-client")
                .contains("scope=openid%20email%20profile");
        MvcResult github = mockMvc.perform(get("/oauth2/authorization/github")).andReturn();
        assertThat(github.getResponse().getRedirectedUrl())
                .startsWith("https://github.com/login/oauth/authorize").contains("state=").contains("scope=read:user%20user:email");
    }

    @Test
    void loginPageOffersBothSocialButtons() throws Exception {
        String page = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("href=\"/oauth2/authorization/google\"").contains("Google로 계속하기")
                .contains("href=\"/oauth2/authorization/github\"").contains("GitHub로 계속하기");
    }
}
