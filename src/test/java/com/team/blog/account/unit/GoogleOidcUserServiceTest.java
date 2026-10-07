package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import com.team.blog.account.infra.GoogleOidcUserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** 001 T150: Google OIDC 응답 정리(FR-004, FR-022). */
class GoogleOidcUserServiceTest {

    private static DefaultOidcUser user(Map<String, Object> claims) {
        OidcIdToken.Builder token = OidcIdToken.withTokenValue("id-token")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(token::claim);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token.build());
    }

    @Test
    void subIdentifiesAndVerifiedEmailIsUsed() {
        SocialProfile profile = GoogleOidcUserService.toProfile(user(Map.of(
                "sub", "109876543210", "email", "Kim.Min@Gmail.com", "email_verified", true,
                "name", "Kim Min-seo", "picture", "https://lh3.googleusercontent.com/a/x")));
        assertThat(profile.provider()).isEqualTo(Provider.GOOGLE);
        assertThat(profile.providerUserId()).isEqualTo("109876543210");
        assertThat(profile.verifiedEmail()).isEqualTo("kim.min@gmail.com");
        assertThat(profile.displayName()).isEqualTo("Kim Min-seo");
        assertThat(profile.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/x");
    }

    @Test
    void unverifiedOrMissingEmailIsNotUsed() {
        assertThat(GoogleOidcUserService.toProfile(user(Map.of("sub", "1", "email", "a@x.com", "email_verified", false)))
                .verifiedEmail()).isNull();
        assertThat(GoogleOidcUserService.toProfile(user(Map.of("sub", "1", "email", "a@x.com"))).verifiedEmail()).isNull();
        assertThat(GoogleOidcUserService.toProfile(user(Map.of("sub", "1"))).verifiedEmail()).isNull();
    }
}
