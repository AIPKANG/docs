package com.team.blog.account.infra;

import com.team.blog.account.application.SocialProfileHolder;
import com.team.blog.account.domain.SocialProfile;
import java.io.Serial;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/** GitHub OAuth2 사용자 + 정리된 {@link SocialProfile}. 이름 속성은 숫자 {@code id}. */
public class SocialOAuth2User extends DefaultOAuth2User implements SocialProfileHolder {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SocialProfile profile;

    public SocialOAuth2User(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes,
                            String nameAttributeKey, SocialProfile profile) {
        super(authorities, attributes, nameAttributeKey);
        this.profile = profile;
    }

    @Override
    public SocialProfile socialProfile() {
        return profile;
    }
}
