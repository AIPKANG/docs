package com.team.blog.account.infra;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import java.util.Locale;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Google OIDC 사용자 정리(research R-7): {@code sub}로 식별, {@code email_verified = true}일 때만 이메일 사용,
 * {@code name}을 표시 이름으로, {@code picture}는 화면 전달용.
 */
@Component
public class GoogleOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser user = super.loadUser(userRequest);
        return new SocialOidcUser(user.getAuthorities(), user.getIdToken(), user.getUserInfo(), toProfile(user));
    }

    public static SocialProfile toProfile(OidcUser user) {
        String email = Boolean.TRUE.equals(user.getEmailVerified()) && user.getEmail() != null
                ? user.getEmail().strip().toLowerCase(Locale.ROOT) : null;
        return new SocialProfile(Provider.GOOGLE, user.getSubject(), email, user.getFullName(), user.getPicture());
    }
}
