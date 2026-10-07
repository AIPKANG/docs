package com.team.blog.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 001 T137: 로그인 후 이동은 상대 경로만(FR-027, research R-12). */
class RedirectTargetValidatorTest {

    @Test
    void relativePathsAreKept() {
        assertThat(RedirectTargetValidator.sanitize("/manage/posts")).isEqualTo("/manage/posts");
        assertThat(RedirectTargetValidator.sanitize("/@kim?tab=posts")).isEqualTo("/@kim?tab=posts");
        assertThat(RedirectTargetValidator.sanitize("/")).isEqualTo("/");
    }

    @Test
    void anythingThatCouldLeaveTheSiteFallsBackToRoot() {
        for (String bad : new String[] {
                "https://evil.example", "http://evil.example/x", "//evil.example", "///evil.example", "/\\evil",
                "\\\\evil.example", "/\tevil", "/ok\r\nLocation: https://evil.example", "/ok\u0000", "javascript:alert(1)",
                "evil.example", "", " /manage", "/a b", null}) {
            assertThat(RedirectTargetValidator.sanitize(bad)).as(String.valueOf(bad)).isEqualTo("/");
        }
    }
}
