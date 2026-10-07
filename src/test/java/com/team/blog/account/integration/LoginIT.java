package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.PASSWORD;
import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.account.infra.KeyHashing;
import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T138: 이메일 로그인(FR-024~FR-029, SC-005, SC-006). */
class LoginIT extends IntegrationTestBase {

    private static final String EMAIL = "login@x.com";
    private static final String FAILURE = "이메일 또는 비밀번호가 올바르지 않아요";
    private static final String LOCKED = "잠시 후 다시 시도해 주세요(약 15분)";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long memberId;

    @BeforeEach
    void member() {
        memberId = members.localMember("loginuser", "로그인", EMAIL, PASSWORD, true);
    }

    private String follow(Browser browser, MvcResult result) throws Exception {
        return browser.perform(get(result.getResponse().getHeader("Location"))).getResponse().getContentAsString();
    }

    @Test
    void successRecordsLastLoginRenewsSessionIdAndGoesToValidatedRedirect() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(get("/login"));
        String before = browser.sessionCookieValue();
        assertThat(before).isNotNull();

        MvcResult result = browser.perform(login("  LOGIN@x.com ", PASSWORD).param("redirect", "/manage/posts"));

        assertThat(result.getResponse().getStatus()).isEqualTo(303);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/manage/posts");
        assertThat(browser.sessionCookieValue()).isNotNull().isNotEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM auth_identity WHERE member_id = ?",
                Boolean.class, memberId)).isTrue();
        assertThat(browser.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void externalRedirectTargetsAreIgnored() throws Exception {
        for (String target : new String[] {"https://evil.example", "//evil.example", "/\\evil"}) {
            MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD).param("redirect", target)).andReturn();
            assertThat(result.getResponse().getHeader("Location")).as(target).isEqualTo("/");
        }
    }

    @Test
    void savedRequestIsUsedWhenNoRedirectIsGiven() throws Exception {
        Browser browser = new Browser(mockMvc);
        MvcResult protectedPage = browser.perform(get("/signup/verify-sent"));
        assertThat(protectedPage.getResponse().getRedirectedUrl()).endsWith("/login");
        MvcResult result = browser.perform(login(EMAIL, PASSWORD));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/signup/verify-sent");
    }

    @Test
    void unknownEmailAndWrongPasswordLookExactlyTheSame() throws Exception {
        Browser a = new Browser(mockMvc);
        Browser b = new Browser(mockMvc);
        MvcResult wrongPassword = a.perform(login(EMAIL, "Wrong#2026x"));
        MvcResult unknownEmail = b.perform(login("nobody@x.com", PASSWORD));
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(303);
        assertThat(wrongPassword.getResponse().getHeader("Location"))
                .isEqualTo(unknownEmail.getResponse().getHeader("Location")).isEqualTo("/login?error");
        String pageA = follow(a, wrongPassword);
        String pageB = follow(b, unknownEmail);
        assertThat(pageA).contains(FAILURE);
        assertThat(stripCsrf(pageA)).isEqualTo(stripCsrf(pageB));
    }

    @Test
    void fiveFailuresLockTheAccountForFifteenMinutes() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(mockMvc.perform(login(EMAIL, "Wrong#2026x")).andReturn().getResponse().getHeader("Location"))
                    .isEqualTo("/login?error");
        }
        Browser browser = new Browser(mockMvc);
        MvcResult locked = browser.perform(login(EMAIL, PASSWORD));
        assertThat(locked.getResponse().getHeader("Location")).isEqualTo("/login?locked");
        assertThat(follow(browser, locked)).contains(LOCKED);

        String lockKey = "auth:login-lock:" + KeyHashing.emailHash(EMAIL);
        assertThat(redis.getExpire(lockKey, TimeUnit.SECONDS)).isBetween(890L, 900L);

        redis.delete(lockKey); // 15분이 지난 것과 같은 상태
        assertThat(mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getHeader("Location")).isEqualTo("/");
    }

    @Test
    void successResetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(EMAIL, "Wrong#2026x"));
        }
        assertThat(mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getHeader("Location")).isEqualTo("/");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(EMAIL, "Wrong#2026x"));
        }
        assertThat(mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getHeader("Location")).isEqualTo("/");
    }

    @Test
    void unknownEmailIsLockedTheSameWay() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("ghost@x.com", "Wrong#2026x"));
        }
        assertThat(mockMvc.perform(login("ghost@x.com", "Wrong#2026x")).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/login?locked");
    }

    @Test
    void twentyFirstAttemptFromOneIpIsBlocked() throws Exception {
        Browser sameIp = new Browser(mockMvc, "10.1.2.3");
        for (int i = 0; i < 20; i++) {
            assertThat(sameIp.perform(login("user" + i + "@x.com", "Wrong#2026x")).getResponse().getHeader("Location"))
                    .as("attempt " + (i + 1)).isEqualTo("/login?error");
        }
        MvcResult blocked = sameIp.perform(login(EMAIL, PASSWORD));
        assertThat(blocked.getResponse().getHeader("Location")).isEqualTo("/login?locked");
        // 다른 IP는 영향 없음
        assertThat(new Browser(mockMvc, "10.9.9.9").perform(login(EMAIL, PASSWORD)).getResponse().getHeader("Location"))
                .isEqualTo("/");
    }

    @Test
    void redisKeysNeverContainTheRawEmail() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(EMAIL, "Wrong#2026x"));
        }
        Set<String> keys = redis.keys("auth:*");
        assertThat(keys).isNotEmpty().allMatch(k -> !k.contains("@") && !k.contains("login@") && !k.contains("x.com"));
        assertThat(keys).contains("auth:login-lock:" + KeyHashing.emailHash(EMAIL));
    }

    private static String stripCsrf(String html) {
        return html.replaceAll("name=\"_csrf\" content=\"[^\"]*\"", "").replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "");
    }
}
