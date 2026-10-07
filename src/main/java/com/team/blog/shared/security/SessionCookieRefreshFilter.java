package com.team.blog.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 쿠키 연장(research R-3): 서버 세션은 요청마다 14일 연장되지만 Spring Session은 세션 ID가 바뀔 때만 쿠키를 쓴다.
 * 그래서 하루에 한 번 같은 세션 ID로 쿠키(Max-Age 14일)를 다시 내려 브라우저 쪽 만료도 마지막 활동 기준이 되게 한다.
 */
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

    static final String REFRESHED_AT = "SESSION_COOKIE_REFRESHED_AT";
    private static final Duration INTERVAL = Duration.ofDays(1);

    private final CookieSerializer cookieSerializer;
    private final Clock clock;

    public SessionCookieRefreshFilter(CookieSerializer cookieSerializer, Clock clock) {
        this.cookieSerializer = cookieSerializer;
        this.clock = clock;
    }

    private static boolean isLoggedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && !session.isNew() && isLoggedIn()) {
            long now = clock.millis();
            Object last = session.getAttribute(REFRESHED_AT);
            if (!(last instanceof Long at)) {
                // 로그인 응답이 막 쿠키를 내려 줬다: 기준 시각만 기록
                session.setAttribute(REFRESHED_AT, now);
            } else if (now - at >= INTERVAL.toMillis()) {
                cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, session.getId()));
                session.setAttribute(REFRESHED_AT, now);
            }
        }
        chain.doFilter(request, response);
    }
}
