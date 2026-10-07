package com.team.blog.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.view.RedirectView;

/** SSR 폼 제출 뒤 이동은 {@code 303 See Other}로 한다(contracts/web-routes.md). */
public final class Redirects {

    private Redirects() {
    }

    /** 같은 사이트 안 상대 경로로 303. 모델 값을 쿼리에 붙이지 않는다. */
    public static RedirectView seeOther(String path) {
        RedirectView view = new RedirectView(path, true);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        view.setHttp10Compatible(false);
        return view;
    }
}
