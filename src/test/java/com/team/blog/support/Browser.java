package com.team.blog.support;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 세션 쿠키({@code SESSION}, Spring Session)를 들고 다니는 테스트용 "브라우저". 응답의 {@code Set-Cookie}로 쿠키를 갱신한다.
 * 여러 개를 만들면 같은 회원의 서로 다른 기기(브라우저 A·B)를 흉내 낼 수 있다.
 */
public class Browser {

    public static final String SESSION_COOKIE = "SESSION";

    private final MockMvc mockMvc;
    private final String remoteAddr;
    private Cookie session;

    public Browser(MockMvc mockMvc) {
        this(mockMvc, "127.0.0.1");
    }

    public Browser(MockMvc mockMvc, String remoteAddr) {
        this.mockMvc = mockMvc;
        this.remoteAddr = remoteAddr;
    }

    public MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (session != null) {
            builder.cookie(session);
        }
        builder.with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        });
        MvcResult result = mockMvc.perform(builder).andReturn();
        update(result.getResponse());
        return result;
    }

    private void update(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(SESSION_COOKIE);
        if (cookie == null) {
            return;
        }
        session = cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty() ? null : cookie;
    }

    /** 현재 쿠키 값(없으면 null). */
    public String sessionCookieValue() {
        return session == null ? null : session.getValue();
    }

    public Cookie sessionCookie() {
        return session;
    }

    public void clearCookies() {
        session = null;
    }
}
