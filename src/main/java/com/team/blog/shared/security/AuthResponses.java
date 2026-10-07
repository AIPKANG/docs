package com.team.blog.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 보안 처리기 공용: 303 이동과 로그인 화면 주소 만들기. */
final class AuthResponses {

    /** 세션 속성: 정지 안내 문구(1회용, 로그인 화면이 읽고 지운다). */
    static final String SUSPENDED_NOTICE = "LOGIN_SUSPENDED_NOTICE";

    private AuthResponses() {
    }

    static void seeOther(HttpServletRequest request, HttpServletResponse response, String path) {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", request.getContextPath() + path);
    }

    /** {@code /login?{flag}} + 검증된 {@code redirect} 값 유지. */
    static String loginPage(String flag, String redirect) {
        StringBuilder url = new StringBuilder("/login?").append(flag);
        if (redirect != null && RedirectTargetValidator.isSafe(redirect) && !"/".equals(redirect)) {
            url.append("&redirect=").append(URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        return url.toString();
    }
}
