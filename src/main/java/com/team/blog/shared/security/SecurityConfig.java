package com.team.blog.shared.security;

import com.team.blog.account.application.AccountStatusChecker;
import com.team.blog.account.application.LoginAttemptService;
import com.team.blog.account.application.LoginRecorder;
import com.team.blog.account.infra.GitHubOAuth2UserService;
import com.team.blog.account.infra.GoogleOidcUserService;
import com.team.blog.shared.web.ClientIpResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.session.web.http.CookieSerializer;

/**
 * 보안 설정(Spring Security 7 람다 DSL).
 * <ul>
 *   <li>CSRF: 세션 저장 토큰, JS는 {@code X-CSRF-TOKEN} 헤더로 보낸다(layout/base.html 메타 태그).</li>
 *   <li>보안 헤더: CSP(12 §8, 003에서 저장소·소셜 사진 출처 추가 — {@link ContentSecurityPolicy}), {@code nosniff},
 *       {@code Referrer-Policy}(헌법 IV).</li>
 *   <li>세션: Spring Session Redis, 로그인 시 세션 ID 새로 발급, SecurityContext는 세션에 명시 저장(001 T110).</li>
 *   <li>폼 로그인({@code POST /login}, 이메일·비밀번호)·로그아웃({@code POST /logout}) — 001 US2.</li>
 *   <li>소셜 로그인(Google OIDC·GitHub OAuth2) — 001 US3.</li>
 *   <li>인가: 몇 개의 로그인 필요 화면 외에는 공개. 업무 권한은 URL이 아니라 Service에서 검사한다(헌법 III).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String CSRF_HEADER = "X-CSRF-TOKEN";

    /** 저장된 요청(로그인 후 돌아갈 곳). 이동 주소에 {@code ?continue}를 붙이지 않는다. */
    static RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setMatchingRequestParameterName(null);
        return cache;
    }

    /** SecurityContext는 세션(Spring Session → Redis)에 명시적으로 저장한다(001 T110). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 폼 로그인 인증: BCrypt 비교(없는 이메일도 더미 해시 비교), 비밀번호가 맞은 뒤 정지 여부 확인. */
    @Bean
    public DaoAuthenticationProvider formLoginAuthenticationProvider(UserDetailsService userDetailsService,
                                                                     PasswordEncoder passwordEncoder,
                                                                     AccountStatusChecker accountStatusChecker) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPostAuthenticationChecks(new LoginStatusPostAuthenticationChecks(accountStatusChecker));
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   DaoAuthenticationProvider formLoginAuthenticationProvider,
                                                   LoginAttemptService loginAttemptService,
                                                   AccountStatusChecker accountStatusChecker,
                                                   LoginRecorder loginRecorder,
                                                   ClientIpResolver clientIpResolver,
                                                   CookieSerializer cookieSerializer,
                                                   Clock clock,
                                                   GoogleOidcUserService googleOidcUserService,
                                                   GitHubOAuth2UserService gitHubOAuth2UserService,
                                                   SocialLoginSuccessHandler socialLoginSuccessHandler,
                                                   SocialLoginFailureHandler socialLoginFailureHandler,
                                                   ContentSecurityPolicy contentSecurityPolicy) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        csrfTokenRepository.setHeaderName(CSRF_HEADER);

        http
                // 008: 대체 저장소 업로드는 서명 주소로만 허가한다(저장소 사전 서명 PUT과 같은 모양, CSRF 토큰 없음)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/images/local-upload"))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy.value()))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .requestCache(cache -> cache.requestCache(requestCache()))
                .authenticationProvider(formLoginAuthenticationProvider)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(new FormLoginSuccessHandler(loginAttemptService, accountStatusChecker, loginRecorder))
                        .failureHandler(new FormLoginFailureHandler(loginAttemptService)))
                // 001 US3: Google(OIDC)·GitHub. state 검증은 프레임워크 기본(FR-020)
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .authorizedClientRepository(new NoStoredAuthorizedClientRepository())
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(googleOidcUserService)
                                .userService(gitHubOAuth2UserService))
                        .successHandler(socialLoginSuccessHandler)
                        .failureHandler(socialLoginFailureHandler))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler(new LogoutCleanupSuccessHandler()))
                .addFilterBefore(new LoginAttemptFilter(loginAttemptService, clientIpResolver),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionCookieRefreshFilter(cookieSerializer, clock), SecurityContextHolderFilter.class)
                .addFilterBefore(new RestoreOnlySessionFilter(), AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .authorizeHttpRequests(authorize -> authorize
                        // 001 US1: 가입 후 인증 안내·재발송은 로그인 필요(비회원은 로그인 화면으로)
                        .requestMatchers("/signup/verify-sent", "/auth/verify/resend").authenticated()
                        // 001 US4: 비밀번호 찾기·재설정은 로그인 전 기능(정지·탈퇴 유예 회원도, 복구 전용 세션 예외 목록에도 있음)
                        .requestMatchers("/password/**").permitAll()
                        // 가입·로그인·인증 링크·주소/닉네임 확인 API·정적 자원과 그 밖의 경로는 공개.
                        // 업무 권한은 URL이 아니라 Service(AccountGuard)에서 검사한다(헌법 III)
                        .anyRequest().permitAll());
        return http.build();
    }
}
