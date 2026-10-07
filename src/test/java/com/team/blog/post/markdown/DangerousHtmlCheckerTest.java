package com.team.blog.post.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 007 T404·SC-007: 검사기는 위험 6개를 모두 잡고 안전 3개를 잘못 잡지 않는다. */
class DangerousHtmlCheckerTest {

    @Test
    void catchesDangerousSamples() {
        String[] dangerous = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<a href=\"&#106;avascript:alert(1)\">x</a>",
            "<p style=\"background:url(x)\">x</p>",
            "<a href=\" data:text/html,<script>\">x</a>"
        };
        for (String html : dangerous) {
            assertThat(DangerousHtmlChecker.problems(html)).as(html).isNotEmpty();
        }
    }

    @Test
    void acceptsSafeSamples() {
        String[] safe = {
            "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>",
            "<p><a href=\"https://spring.io\" target=\"_blank\" rel=\"noopener noreferrer nofollow ugc\">외부</a></p>",
            "<p><img src=\"https://cdn.devlog.example/blog-images/images/a.webp\" alt=\"onerror=alert(1)\" loading=\"lazy\" /></p>"
        };
        for (String html : safe) {
            assertThat(DangerousHtmlChecker.problems(html)).as(html).isEmpty();
        }
    }
}
