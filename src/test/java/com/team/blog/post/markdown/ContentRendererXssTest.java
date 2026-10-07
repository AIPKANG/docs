package com.team.blog.post.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 007 T405·SC-001: 12 §9-1의 공격 문자열 32개가 모두 무해화된다. */
class ContentRendererXssTest {

    private static final ContentRenderer RENDERER = TestRenderers.create();
    private static final String CDN = TestRenderers.CDN;

    static Stream<String> attacks() {
        return Stream.of(
                // 직접 쓴 태그 (7)
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<svg onload=alert(1)>",
                "<iframe src=\"javascript:alert(1)\"></iframe>",
                "<details open ontoggle=alert(1)>",
                "<div style=\"background:url(javascript:alert(1))\">x</div>",
                "<form action=\"javascript:alert(1)\"><button>x</button></form>",
                // 위험한 링크 주소 (11)
                "[클릭](javascript:alert(1))",
                "[클릭](JaVaScRiPt:alert(1))",
                "[클릭](&#106;avascript:alert(1))",
                "[클릭](&#x6A;&#x61;&#x76;&#x61;&#x73;&#x63;&#x72;&#x69;&#x70;&#x74;:alert(1))",
                "[클릭](javascript&colon;alert(1))",
                "[클릭](%6A%61%76%61%73%63%72%69%70%74:alert(1))",
                "[클릭]( javascript:alert(1))",
                "<javascript:alert(1)>",
                "[x]\n\n[x]: javascript:alert(1)",
                "[클릭](vbscript:msgbox(1))",
                "[클릭](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)",
                // 이미지 (3)
                "![x](javascript:alert(1))",
                "![x](data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+)",
                "![x\" onerror=\"alert(1)](" + CDN + "a.webp)",
                // 속성 탈출 (3)
                "[x](" + CDN + "a.webp\" onclick=\"alert(1))",
                "<https://example.com/\" onmouseover=\"alert(1)>",
                "www.example.com/\"onmouseover=\"alert(1)",
                // 문맥 탈출 (3)
                "```\n</code></pre><script>alert(1)</script>\n```",
                "<<script>script>alert(1)<</script>/script>",
                "<math><mtext><table><mglyph><style><img src=x onerror=alert(1)>",
                // 다른 문법 안 (5)
                "| a | b |\n|---|---|\n| <img src=x onerror=alert(1)> | <script>alert(1)</script> |",
                "- [ ] <script>alert(1)</script>\n- [x] <img src=x onerror=alert(1)>",
                "# <script>alert(1)</script>",
                "> <iframe src=javascript:alert(1)>",
                "**<svg/onload=alert(1)>**");
    }

    @ParameterizedTest
    @MethodSource("attacks")
    void attackIsNeutralized(String attack) {
        String html = RENDERER.render(attack).html();
        assertThat(DangerousHtmlChecker.problems(html)).as(html).isEmpty();
        assertThat(html.toLowerCase()).doesNotContain("<script").doesNotContain("<svg").doesNotContain("<iframe")
                .doesNotContain("<style").doesNotContain("<math").doesNotContain("<form").doesNotContain("<div");
    }

    @org.junit.jupiter.api.Test
    void thereAreThirtyTwoAttacks() {
        assertThat(attacks().count()).isEqualTo(32);
    }
}
