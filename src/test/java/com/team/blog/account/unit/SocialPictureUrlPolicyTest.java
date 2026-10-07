package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialPictureUrlPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 003 T266: 소셜 사진 주소 거르기(11 §4-2, FR-020). */
class SocialPictureUrlPolicyTest {

    private final SocialPictureUrlPolicy policy = new SocialPictureUrlPolicy(
            Map.of(Provider.GOOGLE, "lh3.googleusercontent.com", Provider.GITHUB, "avatars.githubusercontent.com"), 256);

    @Test
    void googleSizeParameterBecomes256() {
        assertThat(policy.sanitize(Provider.GOOGLE, "https://lh3.googleusercontent.com/a/ACg8ocK=s96-c"))
                .contains("https://lh3.googleusercontent.com/a/ACg8ocK=s256-c");
        assertThat(policy.sanitize(Provider.GOOGLE, "https://lh3.googleusercontent.com/a/ACg8ocK"))
                .contains("https://lh3.googleusercontent.com/a/ACg8ocK=s256-c");
        assertThat(policy.sanitize(Provider.GOOGLE, "https://lh3.googleusercontent.com/a-/abc=s400"))
                .contains("https://lh3.googleusercontent.com/a-/abc=s256-c");
    }

    @Test
    void githubGetsSizeQuery() {
        assertThat(policy.sanitize(Provider.GITHUB, "https://avatars.githubusercontent.com/u/12345?v=4"))
                .contains("https://avatars.githubusercontent.com/u/12345?v=4&s=256");
        assertThat(policy.sanitize(Provider.GITHUB, "https://avatars.githubusercontent.com/u/12345"))
                .contains("https://avatars.githubusercontent.com/u/12345?s=256");
        assertThat(policy.sanitize(Provider.GITHUB, "https://avatars.githubusercontent.com/u/12345?s=40&v=4"))
                .contains("https://avatars.githubusercontent.com/u/12345?v=4&s=256");
    }

    @Test
    void otherHostsSchemesAndShapesAreDropped() {
        assertThat(policy.sanitize(Provider.GOOGLE, "http://lh3.googleusercontent.com/a/x=s96-c")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://lh3.googleusercontent.com.evil.com/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://evil.com/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://user@lh3.googleusercontent.com/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://lh3.googleusercontent.com:8443/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://avatars.githubusercontent.com/u/1")).isEmpty();
        assertThat(policy.sanitize(Provider.GITHUB, "https://lh3.googleusercontent.com/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "javascript:alert(1)")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "not a url")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, null)).isEmpty();
        assertThat(policy.sanitize(Provider.LOCAL, "https://lh3.googleusercontent.com/a/x")).isEmpty();
        assertThat(policy.sanitize(Provider.GOOGLE, "https://LH3.googleusercontent.com/a/x"))
                .contains("https://lh3.googleusercontent.com/a/x=s256-c");
    }
}
