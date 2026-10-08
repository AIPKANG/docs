package com.team.blog.shared.web;

import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 공통 레이아웃(머리글의 로그인·로그아웃)에 필요한 값. 표시용일 뿐 권한 판단에 쓰지 않는다(헌법 III). */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class LayoutModelAdvice {

    private final CurrentUserProvider currentUserProvider;

    private final String siteHost;

    public LayoutModelAdvice(CurrentUserProvider currentUserProvider,
                             @org.springframework.beans.factory.annotation.Value("${blog.auth.mail.link-base-url:}") String siteUrl) {
        this.currentUserProvider = currentUserProvider;
        this.siteHost = hostOf(siteUrl);
    }

    /** 가입 화면의 "주소/@" 앞부분 등에 쓰는 사이트 호스트(APP_BASE_URL 기준, 예: {@code blog.example.com}). */
    @ModelAttribute("siteHost")
    public String siteHost() {
        return siteHost;
    }

    static String hostOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url == null ? "" : url.strip());
            if (uri.getHost() == null) {
                return "";
            }
            return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 022: 머리말 [관리] 링크. */
    @ModelAttribute("currentIsAdmin")
    public boolean currentIsAdmin() {
        return currentUserProvider.current().map(u -> "ADMIN".equals(u.role())).orElse(false);
    }

    @ModelAttribute("currentMemberId")
    public Long currentMemberId() {
        return currentUserProvider.current().map(CurrentUser::memberId).orElse(null);
    }
}
