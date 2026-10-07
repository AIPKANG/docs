package com.team.blog.account.web;

import com.team.blog.shared.security.RestoreOnlySessionFilter;
import com.team.blog.shared.web.Redirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 복구 전용 세션의 진입 화면 {@code GET /account/restore}(FR-030). [복구하기]({@code POST /account/restore})의 동작은
 * 회원 탈퇴 기능(13·44) 소유다 — 이 기능은 화면만 둔다.
 */
@Controller
public class AccountRestoreEntryController {

    @GetMapping(RestoreOnlySessionFilter.RESTORE_PATH)
    public Object restore(HttpServletRequest request) {
        if (!RestoreOnlySessionFilter.isRestoreOnly(request)) {
            return Redirects.seeOther("/");
        }
        return "auth/restore";
    }
}
