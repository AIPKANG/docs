package com.team.blog.shared.security;

import java.time.Instant;
import org.springframework.security.authentication.AccountStatusException;

/**
 * Spring {@link AccountStatusException}을 상속해 {@code ProviderManager}가 다른 인증 공급자로 넘기지 않고 바로 실패시킨다.
 * 비밀번호가 맞았지만 진행 중인 정지가 있음 → 로그인 거부 + {@code ACCOUNT_SUSPENDED} 안내(FR-030, 42 P-7). */
public class AccountSuspendedAuthenticationException extends AccountStatusException {

    private final Instant endsAt;
    private final String reason;

    public AccountSuspendedAuthenticationException(Instant endsAt, String reason) {
        super("ACCOUNT_SUSPENDED");
        this.endsAt = endsAt;
        this.reason = reason;
    }

    /** null이면 영구 정지. */
    public Instant getEndsAt() {
        return endsAt;
    }

    public String getReason() {
        return reason;
    }
}
