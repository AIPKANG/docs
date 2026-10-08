package com.team.blog.shared.web;

import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 공통 레이아웃(머리글의 로그인·로그아웃)에 필요한 값. 표시용일 뿐 권한 판단에 쓰지 않는다(헌법 III). */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class LayoutModelAdvice {

    private final CurrentUserProvider currentUserProvider;

    public LayoutModelAdvice(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
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
