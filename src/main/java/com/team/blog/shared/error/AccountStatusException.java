package com.team.blog.shared.error;

/**
 * 계정 상태 거부 → 403 + 이유 코드(42 §4). 403은 계정 상태에만 쓴다.
 * <ul>
 *   <li>{@link Reason#EMAIL_NOT_VERIFIED}: SSR은 안내 화면 + [인증 메일 다시 보내기]</li>
 *   <li>{@link Reason#ACCOUNT_WITHDRAWN}: SSR은 {@code 303 /account/restore}</li>
 * </ul>
 */
public class AccountStatusException extends RuntimeException {

    public enum Reason {
        EMAIL_NOT_VERIFIED,
        ACCOUNT_WITHDRAWN,
        ACCOUNT_SUSPENDED
    }

    private final Reason reason;

    public AccountStatusException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
