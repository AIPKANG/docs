package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MailpitClient;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 003 T255: 비밀번호 변경(US4, FR-024~FR-027, SC-006). */
class PasswordChangeIT extends IntegrationTestBase {

    private static final String PASSWORD = "Blog#2026ok";
    private static final String NEW_PASSWORD = "Fresh#2026go";

    @Autowired
    StringRedisTemplate redis;

    private static MockHttpServletRequestBuilder change(String current, String next, String confirm) {
        return post("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next
                        + "\",\"newPasswordConfirm\":\"" + confirm + "\"}");
    }

    private Browser loggedIn(String email, String password) throws Exception {
        Browser browser = new Browser(mockMvc);
        assertThat(browser.perform(login(email, password)).getResponse().getHeader("Location")).isEqualTo("/");
        return browser;
    }

    private int profileStatus(Browser browser) throws Exception {
        return browser.perform(get("/api/me/profile")).getResponse().getStatus();
    }

    @Test
    void otherDevicesAreLoggedOutCurrentDeviceStaysAndMailIsSent() throws Exception {
        members.localMember("kim755030", "김민서", "kim755030@naver.com", PASSWORD, true);
        Browser a = loggedIn("kim755030@naver.com", PASSWORD);
        Browser b = loggedIn("kim755030@naver.com", PASSWORD);
        String before = a.sessionCookieValue();

        MockHttpServletResponse response = a.perform(change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)).getResponse();
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(a.sessionCookieValue()).isNotNull().isNotEqualTo(before);
        assertThat(profileStatus(a)).isEqualTo(200);
        assertThat(profileStatus(b)).isEqualTo(401);

        assertThat(mockMvc.perform(login("kim755030@naver.com", PASSWORD)).andReturn().getResponse().getHeader("Location"))
                .startsWith("/login?error");
        assertThat(mockMvc.perform(login("kim755030@naver.com", NEW_PASSWORD)).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/");

        MailpitClient.Mail mail = mailpit.awaitMessagesTo("kim755030@naver.com", 1).get(0);
        assertThat(mail.subject()).contains("비밀번호가 변경됐어요");
        assertThat(mail.body()).contains("본인이 아니라면").contains("http://localhost/password/forgot")
                .doesNotContain(NEW_PASSWORD);
    }

    @Test
    void wrongCurrentPasswordFiveTimesLocksForFifteenMinutes() throws Exception {
        long id = members.localMember("kim755030", "김민서", "kim755030@naver.com", PASSWORD, true);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(change("Wrong#2026x", NEW_PASSWORD, NEW_PASSWORD).with(TestAuth.member(id)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"))
                    .andExpect(jsonPath("$.message").value("현재 비밀번호가 맞지 않아요"));
        }
        mockMvc.perform(change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD).with(TestAuth.member(id)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_LOCKED"))
                .andExpect(header().exists("Retry-After"));
        String lockKey = "auth:pw-change-lock:" + id;
        assertThat(redis.getExpire(lockKey, TimeUnit.SECONDS)).isBetween(15 * 60L - 10, 15 * 60L);

        redis.delete(lockKey); // 15분 경과와 같은 상태
        mockMvc.perform(change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD).with(TestAuth.member(id)))
                .andExpect(status().isNoContent());
        assertThat(redis.hasKey("auth:pw-change-fail:" + id)).isFalse();
    }

    @Test
    void newPasswordRules() throws Exception {
        long id = members.localMember("kim755030", "김민서", "kim755030@naver.com", PASSWORD, true);
        mockMvc.perform(change(PASSWORD, PASSWORD, PASSWORD).with(TestAuth.member(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SAME_AS_CURRENT"));
        String body = mockMvc.perform(change(PASSWORD, "short1!", "short1!").with(TestAuth.member(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_SHORT"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.errors[0].message")).contains("8자 이상");
        mockMvc.perform(change(PASSWORD, NEW_PASSWORD, "Other#2026go").with(TestAuth.member(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPasswordConfirm"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_MISMATCH"));
        mockMvc.perform(change(PASSWORD, "kim755030#A1", "kim755030#A1").with(TestAuth.member(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CONTAINS_EMAIL_LOCAL_PART"));
        // 규칙 위반은 잠금 횟수에 들어가지 않는다
        assertThat(redis.hasKey("auth:pw-change-fail:" + id)).isFalse();
    }

    @Test
    void socialAccountsCannotChangePassword() throws Exception {
        long id = members.active("go-kim755030", "김민서");
        members.addSocialIdentity(id, "GOOGLE", "google-sub-1", "kim@gmail.com", Instant.now());
        mockMvc.perform(change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD).with(TestAuth.member(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value("소셜 로그인 계정은 비밀번호를 바꿀 수 없어요"));
    }

    @Test
    void guestsAreRejected() throws Exception {
        mockMvc.perform(change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)).andExpect(status().isUnauthorized());
    }
}
