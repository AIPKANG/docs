package com.team.blog.shared.security;

import com.team.blog.account.application.EmailRules;
import com.team.blog.account.application.IpRateLimitedException;
import com.team.blog.account.application.LoginAttemptService;
import com.team.blog.account.application.LoginLockedException;
import com.team.blog.shared.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code POST /login} 인증 전에 IP 한도·계정 잠금을 본다(FR-025). 걸리면 비밀번호를 확인하지 않고
 * {@code /login?locked}("잠시 후 다시 시도해 주세요(약 15분)")로 보낸다. 없는 이메일도 같은 규칙(SC-006).
 */
public class LoginAttemptFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    public LoginAttemptFilter(LoginAttemptService loginAttemptService, ClientIpResolver clientIpResolver) {
        this.loginAttemptService = loginAttemptService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equals(request.getMethod()) && "/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            loginAttemptService.checkAllowed(EmailRules.normalize(request.getParameter("email")),
                    clientIpResolver.resolve(request));
        } catch (LoginLockedException | IpRateLimitedException e) {
            AuthResponses.seeOther(request, response, AuthResponses.loginPage("locked", request.getParameter("redirect")));
            return;
        }
        chain.doFilter(request, response);
    }
}
