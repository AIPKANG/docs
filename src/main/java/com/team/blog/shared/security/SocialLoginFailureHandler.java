package com.team.blog.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** 소셜 로그인 실패(state 불일치·공급자 오류) → {@code /login?error=social} "소셜 로그인에 실패했어요. 다시 시도해 주세요". */
@Component
public class SocialLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) {
        log.info("소셜 로그인 실패: {}", exception.getClass().getSimpleName());
        AuthResponses.seeOther(request, response, "/login?error=social");
    }
}
