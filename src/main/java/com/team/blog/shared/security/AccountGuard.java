package com.team.blog.shared.security;

import com.team.blog.shared.error.LoginRequiredException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Service 계층 공용 권한 가드. {@code requireWritable}은 001-auth(T128)에서 추가한다. */
@Component
public class AccountGuard {

    /** 비회원이면 {@link LoginRequiredException}(401). */
    public CurrentUser requireLoggedIn(Optional<CurrentUser> currentUser) {
        return currentUser.orElseThrow(LoginRequiredException::new);
    }
}
