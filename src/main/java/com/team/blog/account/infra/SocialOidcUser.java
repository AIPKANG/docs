package com.team.blog.account.infra;

import com.team.blog.account.application.SocialProfileHolder;
import com.team.blog.account.domain.SocialProfile;
import java.io.Serial;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** Google OIDC 사용자 + 정리된 {@link SocialProfile}. */
public class SocialOidcUser extends DefaultOidcUser implements SocialProfileHolder {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SocialProfile profile;

    public SocialOidcUser(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken, OidcUserInfo userInfo,
                          SocialProfile profile) {
        super(authorities, idToken, userInfo, "sub");
        this.profile = profile;
    }

    @Override
    public SocialProfile socialProfile() {
        return profile;
    }
}
