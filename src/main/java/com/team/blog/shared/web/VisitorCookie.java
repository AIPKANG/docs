package com.team.blog.shared.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 비회원 방문자 식별 쿠키 {@code vid}(016 FR-003, 31 §2-1): 무작위 UUID, 1년, HttpOnly·Secure·SameSite=Lax.
 * 조회수 중복 방지에만 쓰는 무작위 식별자다(개인정보 처리방침 안내 대상, FR-018).
 */
public final class VisitorCookie {

    public static final String NAME = "vid";
    private static final Pattern UUID_FORMAT = Pattern.compile("[0-9a-f-]{36}");

    private VisitorCookie() {
    }

    public static Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && UUID_FORMAT.matcher(cookie.getValue()).matches()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** 없으면 발급한다(글 상세를 비회원이 볼 때). */
    public static void ensure(HttpServletRequest request, HttpServletResponse response) {
        if (read(request).isPresent()) {
            return;
        }
        response.addHeader("Set-Cookie", NAME + "=" + UUID.randomUUID() + "; Max-Age=31536000; Path=/; HttpOnly; Secure; SameSite=Lax");
    }
}
