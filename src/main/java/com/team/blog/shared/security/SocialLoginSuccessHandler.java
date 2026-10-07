package com.team.blog.shared.security;

import com.team.blog.account.application.AccountStatusChecker;
import com.team.blog.account.application.LoginRecorder;
import com.team.blog.account.application.LoginStatus;
import com.team.blog.account.application.SocialLoginService;
import com.team.blog.account.application.SocialProfileHolder;
import com.team.blog.account.domain.PendingSocialSignup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;

/**
 * 소셜 인증 성공(state 검증은 프레임워크 — FR-020). OAuth2 인증 정보로는 로그인 상태를 남기지 않고 다음 중 하나로 바꾼다.
 * <ul>
 *   <li>연결된 계정: 폼 로그인과 같은 계정 상태 처리(정지 → {@code /login?suspended}, 탈퇴 유예 → 복구 전용 세션) 후
 *       {@link LoginSessionEstablisher}로 로그인(세션 ID 새로 발급), {@code last_login_at} 기록</li>
 *   <li>처음: 로그인시키지 않고 세션에 {@link PendingSocialSignup}(10분)을 두고 {@code 303 /signup/social}</li>
 * </ul>
 */
@Component
public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialLoginService socialLoginService;
    private final AccountStatusChecker accountStatusChecker;
    private final LoginSessionEstablisher loginSessionEstablisher;
    private final LoginRecorder loginRecorder;
    private final RequestCache requestCache = SecurityConfig.requestCache();

    public SocialLoginSuccessHandler(SocialLoginService socialLoginService, AccountStatusChecker accountStatusChecker,
                                     LoginSessionEstablisher loginSessionEstablisher, LoginRecorder loginRecorder) {
        this.socialLoginService = socialLoginService;
        this.accountStatusChecker = accountStatusChecker;
        this.loginSessionEstablisher = loginSessionEstablisher;
        this.loginRecorder = loginRecorder;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        // OAuth2 인증 정보는 세션에 남기지 않는다 — 우리 회원 principal(이름 = memberId)로만 로그인한다
        forgetAuthentication(request);
        if (!(authentication.getPrincipal() instanceof SocialProfileHolder holder)) {
            AuthResponses.seeOther(request, response, "/login?error=social");
            return;
        }
        SocialLoginService.Resolution resolution = socialLoginService.resolve(holder.socialProfile());
        if (resolution instanceof SocialLoginService.NewSignupRequired newSignup) {
            request.getSession(true).setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, newSignup.pending());
            AuthResponses.seeOther(request, response, "/signup/social");
            return;
        }
        SocialLoginService.ExistingMember existing = (SocialLoginService.ExistingMember) resolution;
        LoginStatus status = accountStatusChecker.checkOnLogin(existing.memberId());
        if (status instanceof LoginStatus.Suspended suspended) {
            request.getSession(true).setAttribute(AuthResponses.SUSPENDED_NOTICE,
                    SuspensionNotice.text(suspended.endsAt(), suspended.reason()));
            AuthResponses.seeOther(request, response, "/login?suspended");
            return;
        }
        String target = FormLoginSuccessHandler.afterLoginTarget(request, response, requestCache, null);
        loginSessionEstablisher.establish(existing.memberId(), existing.role(), request, response);
        loginRecorder.recordLogin(existing.memberId());
        if (status instanceof LoginStatus.WithdrawnPending) {
            RestoreOnlySessionFilter.markRestoreOnly(request);
            target = RestoreOnlySessionFilter.RESTORE_PATH;
        }
        AuthResponses.seeOther(request, response, target);
    }

    private static void forgetAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }
}
