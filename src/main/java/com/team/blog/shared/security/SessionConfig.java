package com.team.blog.shared.security;

import com.team.blog.account.application.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

/**
 * 로그인 세션 저장소(research R-3). Boot 자동 설정이 {@code spring.session.data.redis.repository-type=indexed}로
 * {@link RedisIndexedSessionRepository}를 만든다 — principal 이름 인덱스(= memberId)로 회원별 세션을 찾을 수 있다
 * (모든 기기 로그아웃 FR-019). 최대 비활성 시간은 {@code blog.auth.session-timeout}(14일)으로 맞춘다.
 * 세션 쿠키 속성은 {@code server.servlet.session.cookie.*}(HttpOnly·Secure·SameSite=Lax·Max-Age 14일).
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {

    @Bean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionTimeoutCustomizer(AuthProperties properties) {
        return repository -> repository.setDefaultMaxInactiveInterval(properties.sessionTimeout());
    }
}
