package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.SocialProfileHolder;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import com.team.blog.account.infra.GitHubOAuth2UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 001 T151: GitHub 응답 정리(숫자 id 식별, primary && verified 이메일만, name 없으면 login). */
class GitHubOAuth2UserServiceTest {

    @Test
    void numericIdIdentifiesRegardlessOfLogin() {
        SocialProfile before = GitHubOAuth2UserService.toProfile(Map.of("id", 583231, "login", "octocat", "name", "The Octocat"), List.of());
        SocialProfile renamed = GitHubOAuth2UserService.toProfile(Map.of("id", 583231, "login", "octo-renamed"), List.of());
        assertThat(before.provider()).isEqualTo(Provider.GITHUB);
        assertThat(before.providerUserId()).isEqualTo("583231").isEqualTo(renamed.providerUserId());
        assertThat(before.displayName()).isEqualTo("The Octocat");
        assertThat(renamed.displayName()).isEqualTo("octo-renamed");
    }

    @Test
    void onlyPrimaryAndVerifiedEmailIsUsed() {
        List<Map<String, Object>> emails = List.of(
                Map.of("email", "old@x.com", "primary", false, "verified", true),
                Map.of("email", "Main@X.com", "primary", true, "verified", true));
        assertThat(GitHubOAuth2UserService.toProfile(Map.of("id", 1L, "login", "a"), emails).verifiedEmail()).isEqualTo("main@x.com");

        List<Map<String, Object>> unverifiedPrimary = List.of(
                Map.of("email", "main@x.com", "primary", true, "verified", false),
                Map.of("email", "other@x.com", "primary", false, "verified", true));
        assertThat(GitHubOAuth2UserService.toProfile(Map.of("id", 1L, "login", "a"), unverifiedPrimary).verifiedEmail()).isNull();
        assertThat(GitHubOAuth2UserService.toProfile(Map.of("id", 1L, "login", "a"), null).verifiedEmail()).isNull();
    }

    @Test
    void blankNameFallsBackToLogin() {
        assertThat(GitHubOAuth2UserService.toProfile(Map.of("id", 2, "login", "kim", "name", " "), List.of()).displayName())
                .isEqualTo("kim");
    }

    @Test
    void loadUserCombinesUserAndEmailsResponses() {
        OAuth2User fixed = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("id", 42, "login", "dev", "avatar_url", "https://avatars.githubusercontent.com/u/42"), "id");
        GitHubOAuth2UserService service = new GitHubOAuth2UserService(request -> fixed,
                request -> List.of(Map.of("email", "dev@x.com", "primary", true, "verified", true)));
        OAuth2User loaded = service.loadUser(null);
        assertThat(loaded.getName()).isEqualTo("42");
        assertThat(loaded).isInstanceOf(SocialProfileHolder.class);
        SocialProfile profile = ((SocialProfileHolder) loaded).socialProfile();
        assertThat(profile.verifiedEmail()).isEqualTo("dev@x.com");
        assertThat(profile.pictureUrl()).isEqualTo("https://avatars.githubusercontent.com/u/42");
    }
}
