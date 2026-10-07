package com.team.blog.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 보안 기본 설정(Spring Security 7 람다 DSL).
 * <ul>
 *   <li>CSRF: 세션 저장 토큰, JS는 {@code X-CSRF-TOKEN} 헤더로 보낸다(layout/base.html 메타 태그).</li>
 *   <li>보안 헤더: CSP(12 §8), {@code nosniff}, {@code Referrer-Policy}(헌법 IV).</li>
 *   <li>인가: 아직 {@code permitAll}. 업무 권한은 URL이 아니라 Service에서 검사한다(헌법 III).
 *       세션·가입 경로·폼 로그인·OAuth2는 001-auth가 이 파일에 추가한다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** CDN 허용은 008에서 추가한다. */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; img-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    public static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        csrfTokenRepository.setHeaderName(CSRF_HEADER);

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
