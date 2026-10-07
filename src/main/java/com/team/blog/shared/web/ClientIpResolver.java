package com.team.blog.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 요청 제한용 클라이언트 IP. {@code server.forward-headers-strategy=native}로 신뢰 프록시의 {@code X-Forwarded-For}만
 * 반영된 {@code request.getRemoteAddr()}를 쓴다(헤더를 직접 읽지 않는다 — 001 R-6과 같은 규칙).
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
