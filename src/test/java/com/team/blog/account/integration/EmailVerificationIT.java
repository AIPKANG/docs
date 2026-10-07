package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.account.infra.KeyHashing;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MailpitClient;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 001 T123: 인증 링크·재발송(FR-008~FR-010, SC-007). */
class EmailVerificationIT extends IntegrationTestBase {

    private static final String EMAIL = "verify@x.com";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long memberId;

    @BeforeEach
    void signUp() throws Exception {
        mockMvc.perform(signup(EMAIL, "verifyme", "인증해요"));
        memberId = jdbc.queryForObject("SELECT member_id FROM auth_identity WHERE email = ?", Long.class, EMAIL);
    }

    private String latestToken(int expectedMails) {
        List<MailpitClient.Mail> mails = mailpit.awaitMessagesTo(EMAIL, expectedMails);
        assertThat(mails).hasSize(expectedMails);
        return mails.get(mails.size() - 1).token("/auth/verify").orElseThrow();
    }

    private String visit(String token) throws Exception {
        return mockMvc.perform(get("/auth/verify").param("token", token)).andReturn().getResponse().getContentAsString();
    }

    private String resend() throws Exception {
        return mockMvc.perform(post("/auth/verify/resend").with(csrf()).with(TestAuth.member(memberId)))
                .andReturn().getResponse().getContentAsString();
    }

    private boolean verified() {
        return jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM auth_identity WHERE member_id = ?",
                Boolean.class, memberId);
    }

    @Test
    void validLinkVerifiesOnceAndReuseShowsExpired() throws Exception {
        String token = latestToken(1);
        assertThat(visit(token)).contains("인증이 완료됐어요");
        assertThat(verified()).isTrue();
        assertThat(visit(token)).contains("링크가 만료됐어요").contains("인증 메일 다시 보내기");
    }

    @Test
    void linkExpiresWithItsRedisTtl() throws Exception {
        String token = latestToken(1);
        String key = "auth:verify:" + KeyHashing.sha256(token);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(24 * 3600L - 10, 24 * 3600L);
        redis.expire(key, Duration.ofMillis(1)); // 24시간이 지난 것과 같은 상태
        Thread.sleep(20);
        assertThat(visit(token)).contains("링크가 만료됐어요");
        assertThat(verified()).isFalse();
    }

    @Test
    void resendIsLimitedToOncePerMinuteAndInvalidatesThePreviousLink() throws Exception {
        String first = latestToken(1);
        assertThat(resend()).contains("인증 메일을 다시 보냈어요");
        String second = latestToken(2);
        assertThat(second).isNotEqualTo(first);

        assertThat(resend()).contains("잠시 후 다시 시도해 주세요");
        assertThat(mailpit.countAfterQuietPeriod(EMAIL)).isEqualTo(2);

        assertThat(visit(first)).contains("링크가 만료됐어요");
        assertThat(verified()).isFalse();
        assertThat(visit(second)).contains("인증이 완료됐어요");
    }

    @Test
    void resendIsLimitedToTenPerDay() throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertThat(resend()).as("resend " + i).contains("인증 메일을 다시 보냈어요");
            redis.delete("auth:verify-resend:" + memberId + ":min"); // 1분이 지난 것과 같은 상태
        }
        assertThat(resend()).contains("잠시 후 다시 시도해 주세요");
        assertThat(mailpit.awaitMessagesTo(EMAIL, 11)).hasSize(11);
        assertThat(mailpit.countAfterQuietPeriod(EMAIL)).isEqualTo(11);
    }

    @Test
    void alreadyVerifiedAccountIsTold() throws Exception {
        visit(latestToken(1));
        assertThat(resend()).contains("이미 인증된 계정이에요");
    }

    @Test
    void resendRequiresLogin() throws Exception {
        int status = mockMvc.perform(post("/auth/verify/resend").with(csrf())).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(302);
    }
}
