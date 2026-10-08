package com.team.blog.moderation.application;

import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 관리자 화면·API(42 P-10): 비회원 401, 관리자가 아니면(인증 전 포함) 없는 주소와 같은 404. */
@Component
public class AdminGuard {

    private final AccountGuard accountGuard;

    public AdminGuard(AccountGuard accountGuard) {
        this.accountGuard = accountGuard;
    }

    public CurrentUser requireAdmin(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        if (!"ADMIN".equals(user.role())) {
            throw new NotFoundException();
        }
        return user;
    }
}
