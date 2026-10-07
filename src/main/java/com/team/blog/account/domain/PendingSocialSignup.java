package com.team.blog.account.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 소셜 첫 로그인 후 가입 마무리 전까지의 대기 정보(FR-021, research R-7). 세션 속성 {@link #SESSION_ATTRIBUTE}에만 두고,
 * {@code pending-social-ttl}(10분)이 지나면 무효다. 이 동안 계정은 만들지 않는다.
 */
public record PendingSocialSignup(Provider provider, String providerUserId, String verifiedEmail, String displayName,
                                  String pictureUrl, Instant createdAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SESSION_ATTRIBUTE = "PENDING_SOCIAL_SIGNUP";

    public PendingSocialSignup {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(providerUserId);
        Objects.requireNonNull(createdAt);
    }

    public static PendingSocialSignup from(SocialProfile profile, Instant now) {
        return new PendingSocialSignup(profile.provider(), profile.providerUserId(), profile.verifiedEmail(),
                profile.displayName(), profile.pictureUrl(), now);
    }

    public boolean isExpired(Instant now, Duration ttl) {
        return !now.isBefore(createdAt.plus(ttl));
    }

    public boolean hasVerifiedEmail() {
        return verifiedEmail != null && !verifiedEmail.isBlank();
    }

    @Override
    public String toString() {
        return "PendingSocialSignup[" + provider + ":" + providerUserId + ", createdAt=" + createdAt + "]";
    }
}
