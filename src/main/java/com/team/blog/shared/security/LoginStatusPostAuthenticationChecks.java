package com.team.blog.shared.security;

import com.team.blog.account.application.AccountStatusChecker;
import com.team.blog.account.application.LoginStatus;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * 폼 로그인에서 <b>비밀번호가 맞은 뒤</b> 계정 상태를 본다({@code DaoAuthenticationProvider}의 사후 검사).
 * 진행 중 정지 → {@link AccountSuspendedAuthenticationException}(로그인 안 됨). 비밀번호가 틀리면 여기까지 오지 않으므로
 * 정지 여부가 드러나지 않는다(R-9). 탈퇴 유예는 로그인시킨 뒤 성공 처리기가 복구 전용 세션으로 만든다.
 */
public class LoginStatusPostAuthenticationChecks implements UserDetailsChecker {

    private final AccountStatusChecker accountStatusChecker;

    public LoginStatusPostAuthenticationChecks(AccountStatusChecker accountStatusChecker) {
        this.accountStatusChecker = accountStatusChecker;
    }

    @Override
    public void check(UserDetails user) {
        if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("credentials expired");
        }
        if (user instanceof MemberPrincipal principal
                && accountStatusChecker.checkOnLogin(principal.memberId()) instanceof LoginStatus.Suspended s) {
            throw new AccountSuspendedAuthenticationException(s.endsAt(), s.reason());
        }
    }
}
