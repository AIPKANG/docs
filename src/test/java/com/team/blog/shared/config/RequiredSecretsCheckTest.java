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
        RequiredSecretsCheck.REQUIRED_S3.forEach(k -> env.setProperty(k, "value"));
        new RequiredSecretsCheck(env).afterPropertiesSet();
        assertThat(RequiredSecretsCheck.missing(env)).isEmpty();
    }

    /** 사진을 디스크에 두면 S3 키 대신 업로드 서명 키만 필요하다. */
    @Test
    void localStorageNeedsOnlyUploadSecret() {
        MockEnvironment env = new MockEnvironment().withProperty("blog.storage.type", "local");
        RequiredSecretsCheck.REQUIRED.forEach(k -> env.setProperty(k, "value"));
        assertThat(RequiredSecretsCheck.missing(env)).containsExactly("blog.storage.local-secret");
        env.setProperty("blog.storage.local-secret", "s3cret");
        assertThat(RequiredSecretsCheck.missing(env)).isEmpty();
    }
}
