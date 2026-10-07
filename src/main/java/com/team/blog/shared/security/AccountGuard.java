package com.team.blog.shared.security;

import com.team.blog.shared.error.AccountStatusException;
import com.team.blog.shared.error.AccountStatusException.Reason;
import com.team.blog.shared.error.LoginRequiredException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Service 계층 공용 권한 가드(헌법 III, contracts/account-service.md §1). 쓰기 Service가 메서드 첫 줄에서 부른다.
 */
@Component
public class AccountGuard {

    private final AccountStatusLookup accountStatusLookup;

    public AccountGuard(AccountStatusLookup accountStatusLookup) {
        this.accountStatusLookup = accountStatusLookup;
    }

    /** 비회원이면 {@link LoginRequiredException}(401). 자기 글·댓글 삭제·복구 등. */
    public CurrentUser requireLoggedIn(Optional<CurrentUser> currentUser) {
        return currentUser.orElseThrow(LoginRequiredException::new);
    }

    /**
     * 쓰기(글·댓글·사진·좋아요·신고·프로필 사진) 가드 — 42 §3 순서: 비회원 → 401, 탈퇴 유예 → 403 {@code ACCOUNT_WITHDRAWN},
     * 인증 전 → 403 {@code EMAIL_NOT_VERIFIED}. 인증 여부는 세션이 아니라 DB에서 PK 조회 1회로 읽는다
     * (다른 기기에서 인증해도 즉시 반영, research R-10).
     */
    public CurrentUser requireWritable(Optional<CurrentUser> currentUser) {
        CurrentUser user = requireLoggedIn(currentUser);
        AccountStatusLookup.WriteStatus status = accountStatusLookup.find(user.memberId())
                .orElseThrow(LoginRequiredException::new);
        if (status.anonymized()) {
            throw new LoginRequiredException();
        }
        if (status.withdrawalPending()) {
            throw new AccountStatusException(Reason.ACCOUNT_WITHDRAWN);
        }
        if (!status.emailVerified()) {
            throw new AccountStatusException(Reason.EMAIL_NOT_VERIFIED);
        }
        return user;
    }
}
