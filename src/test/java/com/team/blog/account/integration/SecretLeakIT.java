package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.login;
import static com.team.blog.account.integration.AuthTestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 001 T173: 가입·인증·로그인·재설정 흐름에서 비밀번호·토큰·이메일 원문이 로그와 Redis 키에 남지 않는다(FR-014). */
@ExtendWith(OutputCaptureExtension.class)
class SecretLeakIT extends IntegrationTestBase {

    private static final String EMAIL = "leakcheck@example.org";
    private static final String PASSWORD = "Leak#Check77";
    private static final String NEW_PASSWORD = "Newer#Check88";

    @Autowired
    StringRedisTemplate redis;

    @Test
    void noSecretsInLogsOrRedisKeys(CapturedOutput output) throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(signup(EMAIL, "leakcheck", PASSWORD, PASSWORD, "누출점검", true, true));
        String verifyToken = mailpit.awaitMessagesTo(EMAIL, 1).get(0).token("/auth/verify").orElseThrow();
        Set<String> keysWithToken = redis.keys("*");

        mockMvc.perform(get("/auth/verify").param("token", verifyToken));
        mockMvc.perform(login(EMAIL, "Wrong#Pass99"));
        mockMvc.perform(login(EMAIL, PASSWORD));
        mockMvc.perform(post("/password/forgot").with(csrf()).param("email", EMAIL));
        String resetToken = mailpit.awaitMessagesTo(EMAIL, 2).get(1).token("/password/reset").orElseThrow();
        Set<String> keysWithReset = redis.keys("*");
        mockMvc.perform(post("/password/reset").with(csrf()).param("token", resetToken)
                .param("password", NEW_PASSWORD).param("passwordConfirm", NEW_PASSWORD));

        String logs = output.getAll();
        assertThat(logs).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("Wrong#Pass99")
                .doesNotContain(verifyToken).doesNotContain(resetToken).doesNotContain(EMAIL);
        assertThat(logs).contains("메일 발송"); // 마스킹한 발송 로그는 남는다
        for (Set<String> keys : java.util.List.of(keysWithToken, keysWithReset, redis.keys("*"))) {
            assertThat(keys).noneMatch(k -> k.contains(verifyToken) || k.contains(resetToken) || k.contains(EMAIL)
                    || k.contains("leakcheck@"));
        }
    }
}
