package com.team.blog.account.web;

import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.security.RestoreOnlySessionFilter;
import com.team.blog.shared.web.Redirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 복구 전용 세션의 진입 화면 {@code GET /account/restore}(001 FR-030). 복구 기한·남은 일수와 [복구하기]·[로그아웃](023 FR-019).
 * [복구하기]({@code POST /account/restore})는 {@link WithdrawalController}.
 */
@Controller
public class AccountRestoreEntryController {

    private final WithdrawalController withdrawalController;
    private final CurrentUserProvider currentUserProvider;

    public AccountRestoreEntryController(WithdrawalController withdrawalController, CurrentUserProvider currentUserProvider) {
        this.withdrawalController = withdrawalController;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping(RestoreOnlySessionFilter.RESTORE_PATH)
    public Object restore(HttpServletRequest request, Model model) {
        if (!RestoreOnlySessionFilter.isRestoreOnly(request)) {
            return Redirects.seeOther("/");
        }
        currentUserProvider.current().ifPresent(u -> withdrawalController.restoreModel(model, u.memberId()));
        return "auth/restore";
    }
}
