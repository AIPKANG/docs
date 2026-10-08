package com.team.blog.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 운영 프로필 기동 검사(001 T177): 비밀값은 환경 변수로만 넣고(헌법 IV), 하나라도 비어 있으면 기동하지 않는다.
 * 값은 로그에 남기지 않고 빠진 이름만 알린다.
 */
@Component
@Profile("prod")
public class RequiredSecretsCheck implements InitializingBean {

    static final List<String> REQUIRED = List.of(
            "spring.mail.username",
            "spring.mail.password",
            "spring.security.oauth2.client.registration.google.client-id",
            "spring.security.oauth2.client.registration.google.client-secret",
            "spring.security.oauth2.client.registration.github.client-id",
            "spring.security.oauth2.client.registration.github.client-secret",
            "blog.auth.mail.from",
            "blog.auth.mail.link-base-url");

    /** 사진 저장소가 S3 호환(MinIO)일 때만 필요한 값. */
    static final List<String> REQUIRED_S3 = List.of(
            "blog.storage.endpoint",
            "blog.storage.public-base-url",
            "blog.storage.access-key",
            "blog.storage.secret-key");

    /** 사진을 서버 디스크에 둘 때(운영 기본) 필요한 값: 업로드 주소 서명 키. 재시작해도 같은 키여야 한다. */
    static final List<String> REQUIRED_LOCAL = List.of("blog.storage.local-secret");

    private final Environment environment;

    public RequiredSecretsCheck(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missing = missing(environment);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("운영 프로필에 필요한 설정이 비어 있어요(환경 변수 MAIL_USERNAME, MAIL_PASSWORD, "
                    + "GOOGLE_CLIENT_ID/SECRET, GITHUB_CLIENT_ID/SECRET, APP_BASE_URL, 사진 저장소가 디스크면 STORAGE_LOCAL_SECRET, "
                    + "S3면 STORAGE_ENDPOINT·STORAGE_PUBLIC_BASE_URL·STORAGE_ACCESS_KEY·STORAGE_SECRET_KEY 확인): " + missing);
        }
    }

    static List<String> missing(Environment environment) {
        List<String> missing = new ArrayList<>();
        List<String> keys = new ArrayList<>(REQUIRED);
        keys.addAll("local".equals(environment.getProperty("blog.storage.type")) ? REQUIRED_LOCAL : REQUIRED_S3);
        for (String key : keys) {
            String value;
            try {
                value = environment.getProperty(key);
            } catch (IllegalArgumentException unresolved) {
                value = null;
            }
            if (value == null || value.isBlank() || value.contains("${")) {
                missing.add(key);
            }
        }
        return missing;
    }
}
