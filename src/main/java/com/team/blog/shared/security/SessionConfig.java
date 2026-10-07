package com.team.blog.shared.security;

import com.team.blog.account.application.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 로그인 세션 저장소(research R-3). Boot 자동 설정이 {@code spring.session.data.redis.repository-type=indexed}로
 * {@link RedisIndexedSessionRepository}를 만든다 — principal 이름 인덱스(= memberId)로 회원별 세션을 찾을 수 있다
 * (모든 기기 로그아웃 FR-019). 최대 비활성 시간은 {@code blog.auth.session-timeout}(14일)으로 맞춘다.
 *
 * <p>세션 쿠키(FR-028)는 여기서 명시적으로 만든다: {@code SESSION}, {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax},
 * {@code Path=/}, {@code Max-Age} = 세션 유지 기간. 실행 환경(내장 서버·MockMvc)에 따라 기본 직렬화기 설정이 달라지는 것을 막는다.
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {

    public static final String SESSION_COOKIE = "SESSION";

    @Bean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionTimeoutCustomizer(AuthProperties properties) {
        return repository -> repository.setDefaultMaxInactiveInterval(properties.sessionTimeout());
    }

    @Bean
    public CookieSerializer cookieSerializer(AuthProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge((int) properties.sessionTimeout().toSeconds());
        return serializer;
    }
}
