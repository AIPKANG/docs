package com.team.blog.post.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 007 T406·SC-002: 12 §9-2 정상 문법 13개와 링크·이미지·요약 규칙. */
class ContentRendererSyntaxTest {

    private static final ContentRenderer RENDERER = TestRenderers.create();
    private static final String CDN = TestRenderers.CDN;

    private static String html(String md) {
        String html = RENDERER.render(md).html();
        assertThat(DangerousHtmlChecker.problems(html)).isEmpty();
        return html;
    }

    @Test
    void headingsAreLoweredOneLevelWithKoreanAnchors() {
        String html = html("# 원인\n\n## 해결 방법\n\n###### 여섯");
        assertThat(html).contains("<h2 id=\"h-원인\">원인</h2>").contains("<h3 id=\"h-해결-방법\">해결 방법</h3>")
                .contains("<h6 id=\"h-여섯\">여섯</h6>").doesNotContain("<h1");
    }

    @Test
    void duplicateAnchorsGetSuffix() {
        String html = html("# 정리\n\n# 정리\n\n# 정리\n\n# Spring Boot!");
        assertThat(html).contains("id=\"h-정리\"").contains("id=\"h-정리-1\"").contains("id=\"h-정리-2\"")
                .contains("id=\"h-spring-boot\"");
    }

    @Test
    void inlineStyles() {
        assertThat(html("**굵게** *기울임* ~~취소~~ `inline`"))
                .contains("<strong>굵게</strong>").contains("<em>기울임</em>").contains("<del>취소</del>")
                .contains("<code>inline</code>");
    }

    @Test
    void tableWithAlignment() {
        String html = html("| 이름 | 값 |\n|:---|---:|\n| a | 1 |");
        assertThat(html).contains("<table>").contains("<th align=\"left\">이름</th>").contains("<td align=\"right\">1</td>");
    }

    @Test
    void taskListIsReadOnly() {
        String html = html("- [x] 완료\n- [ ] 할 일");
        assertThat(html).contains("type=\"checkbox\"").contains("disabled").contains("checked");
        assertThat(html.split("disabled", -1)).hasSize(3);
    }

    @Test
    void codeBlockIsEscapedWithLanguageClass() {
        String html = html("```java\nList<String> xs = new ArrayList<>();\n```\n\n```evil\" onclick=\"x\nx\n```");
        assertThat(html).contains("<pre><code class=\"language-java\">List&lt;String&gt; xs");
        assertThat(html).doesNotContain("onclick");
    }

    @Test
    void listsQuotesRules() {
        String html = html("- a\n- b\n\n3. c\n4. d\n\n> 인용\n\n---");
        assertThat(html).contains("<ul>").contains("<ol start=\"3\">").contains("<blockquote>").contains("<hr");
    }

    @Test
    void internalLinkSameTabExternalLinkNewTab() {
        String html = html("[내부](/@kim755030/posts/1) [외부](https://spring.io) [우리](https://devlog.example/@a) <https://x.dev>");
        assertThat(html).containsPattern("<a href=\"/(&#64;|@)kim755030/posts/1\">내부</a>");
        assertThat(html).contains("href=\"https://spring.io\"").contains("rel=\"noopener noreferrer nofollow ugc\"")
                .contains("target=\"_blank\"");
        assertThat(html).containsPattern("<a href=\"https://devlog.example/(&#64;|@)a\">우리</a>");
        assertThat(html).containsPattern("<a [^>]*href=\"https://x.dev\"[^>]*target=\"_blank\"");
        assertThat(html).doesNotContain("href=\"//");
    }

    @Test
    void protocolRelativeLinkIsExternal() {
        assertThat(html("[x](//evil.example/a)")).contains("target=\"_blank\"");
    }

    @Test
    void ownImageShownExternalImageBecomesLink() {
        RenderedContent r = RENDERER.render("![업로드](" + CDN + "2026/10/a.webp)\n\n![외부 배지](https://img.shields.io/x.svg)\n\n![](https://ex.example/p.png)");
        assertThat(r.html()).contains("<img src=\"" + CDN + "2026/10/a.webp\" alt=\"업로드\" loading=\"lazy\" decoding=\"async\"");
        assertThat(r.html()).contains(">[이미지] 외부 배지</a>").contains("[이미지] https://ex.example/p.png");
        assertThat(r.html()).doesNotContain("img.shields.io/x.svg\" alt").doesNotContain("<img src=\"https://ex");
        assertThat(r.imageUrls()).containsExactly(CDN + "2026/10/a.webp");
    }

    @Test
    void lookalikeStoragePathIsNotOurs() {
        String html = html("![x](" + CDN + "../secret.png)\n\n![y](https://cdn.devlog.example.evil/blog-images/images/a.webp)");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void rawHtmlIsShownAsText() {
        assertThat(html("<b>직접 쓴 HTML</b>")).contains("&lt;b&gt;직접 쓴 HTML&lt;/b&gt;");
    }

    @Test
    void autolinkAndStrikethroughAndHardBreak() {
        String html = html("https://example.com 방문\\\n다음 줄");
        assertThat(html).contains("href=\"https://example.com\"").contains("<br");
    }

    @Test
    void excerptSkipsCodeImagesAndTables() {
        String md = "# 제목\n\n본문 `코드` [링크](https://a.b) 글자\n\n```\n빼는 코드\n```\n\n| 표 |\n|---|\n| 칸 |\n\n![사진](" + CDN + "a.webp)\n\n- 목록 하나\n- 둘";
        assertThat(RENDERER.render(md).excerpt()).isEqualTo("제목 본문 코드 링크 글자 목록 하나 둘");
    }

    @Test
    void excerptIsCutBeforeWordAt200() {
        String md = "가".repeat(195) + " 다섯글자단어 끝";
        String excerpt = RENDERER.render(md).excerpt();
        assertThat(excerpt).isEqualTo("가".repeat(195));
        assertThat(RENDERER.render("짧은 글").excerpt()).isEqualTo("짧은 글");
        assertThat(RENDERER.render("").excerpt()).isEmpty();
    }
}
