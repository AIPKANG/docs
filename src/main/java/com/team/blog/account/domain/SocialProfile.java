package com.team.blog.account.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 소셜 공급자가 확인해 준 사용자 정보(FR-004, FR-022).
 *
 * @param providerUserId Google {@code sub} / GitHub 숫자 {@code id} 문자열 — 바뀔 수 있는 로그인 이름은 쓰지 않는다
 * @param verifiedEmail  공급자가 인증한 이메일(소문자)만, 없으면 null
 * @param displayName    표시 이름(닉네임 미리 채우기용)
 * @param pictureUrl     프로필 사진 주소(화면 전달용, DB 저장 안 함)
 */
public record SocialProfile(Provider provider, String providerUserId, String verifiedEmail, String displayName,
                            String pictureUrl) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SocialProfile {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(providerUserId);
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("social profile must not be LOCAL");
        }
    }

    @Override
    public String toString() {
        return "SocialProfile[" + provider + ":" + providerUserId + "]";
    }
}
