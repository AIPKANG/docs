package com.team.blog.account.application;

import java.time.Instant;

/**
 * 설정 화면·{@code GET /api/me/profile} 값(contracts/web-routes.md §2). 블로그 주소·이메일·로그인 수단은 읽기 전용이다.
 *
 * @param nicknameNextAllowedAt 30일 제한 중이면 다음 변경 가능 시각, 아니면 null
 */
public record ProfileView(String handle, String nickname, String bio, Long profileImageId, String profileImageUrl,
                          String email, String provider, boolean emailVerified, String defaultVisibility,
                          Instant nicknameNextAllowedAt) {

    public ProfileAvatar avatar() {
        return new ProfileAvatar(handle, nickname, profileImageUrl);
    }

    public boolean passwordChangeSupported() {
        return "LOCAL".equals(provider);
    }
}
