package com.team.blog.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 복구 전용 세션(탈퇴 유예 회원의 로그인, 42 P-12): {@code /account/restore}, {@code /logout}, 비밀번호 찾기·재설정,
 * 정적 자원 외의 모든 요청을 {@code 303 /account/restore}로 보낸다.
 */
public class RestoreOnlySessionFilter extends OncePerRequestFilter {

    public static final String RESTORE_ONLY = "RESTORE_ONLY_SESSION";
    public static final String RESTORE_PATH = "/account/restore";

    private static final List<String> ALLOWED_PREFIXES = List.of(
            RESTORE_PATH, "/logout", "/password/", "/js/", "/css/", "/fonts/", "/images/", "/webjars/", "/error");
    private static final List<String> ALLOWED_EXACT = List.of("/favicon.ico", "/password");

    public static void markRestoreOnly(HttpServletRequest request) {
        request.getSession(true).setAttribute(RESTORE_ONLY, Boolean.TRUE);
    }

    public static boolean isRestoreOnly(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(RESTORE_ONLY));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isRestoreOnly(request) && !allowed(request)) {
            AuthResponses.seeOther(request, response, RESTORE_PATH);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_EXACT.contains(path) || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
