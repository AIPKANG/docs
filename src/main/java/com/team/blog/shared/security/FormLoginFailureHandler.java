package com.team.blog.shared.security;

import com.team.blog.account.application.EmailRules;
import com.team.blog.account.application.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 폼 로그인 실패. 정지(비밀번호는 맞음)면 {@code /login?suspended}, 그 밖에는 실패 카운터 +1 후 항상 같은
 * {@code /login?error}("이메일 또는 비밀번호가 올바르지 않아요" — FR-026, SC-006).
 */
public class FormLoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public FormLoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) {
        String redirect = request.getParameter("redirect");
        if (exception instanceof AccountSuspendedAuthenticationException suspended) {
            request.getSession(true).setAttribute(AuthResponses.SUSPENDED_NOTICE,
                    SuspensionNotice.text(suspended.getEndsAt(), suspended.getReason()));
            AuthResponses.seeOther(request, response, AuthResponses.loginPage("suspended", redirect));
            return;
        }
        loginAttemptService.recordFailure(EmailRules.normalize(request.getParameter("email")));
        AuthResponses.seeOther(request, response, AuthResponses.loginPage("error", redirect));
    }
}
