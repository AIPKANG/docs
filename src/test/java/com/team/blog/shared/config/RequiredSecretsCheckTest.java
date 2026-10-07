package com.team.blog.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 001 T177: 운영 프로필은 비밀값이 없으면 기동하지 않는다. */
class RequiredSecretsCheckTest {

    @Test
    void missingSecretsStopStartupWithoutPrintingValues() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.mail.username", "blog@gmail.com")
                .withProperty("spring.mail.password", "");
        assertThatThrownBy(() -> new RequiredSecretsCheck(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.password")
                .hasMessageContaining("spring.security.oauth2.client.registration.google.client-id")
                .hasMessageNotContaining("blog@gmail.com");
    }

    @Test
    void allPresentPasses() {
        MockEnvironment env = new MockEnvironment();
        RequiredSecretsCheck.REQUIRED.forEach(k -> env.setProperty(k, "value"));
        new RequiredSecretsCheck(env).afterPropertiesSet();
        assertThat(RequiredSecretsCheck.missing(env)).isEmpty();
    }
}
