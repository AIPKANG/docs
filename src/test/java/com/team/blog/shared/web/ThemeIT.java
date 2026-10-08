package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 024 다크 모드: 그리기 전 테마 결정(외부 스크립트), 색 역할만 쓰기, 인라인 스크립트 없음, 스크립트 없으면 버튼 숨김. */
class ThemeIT extends IntegrationTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");

    @Test
    void everyPageDecidesThemeBeforePaintWithoutInlineScripts() throws Exception {
        long me = members.localMember("themeuser", "themeuser", "themeuser@example.com", "Blog#2026ok", true);
        for (String path : List.of("/", "/tags", "/login", "/signup")) {
            String html = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertHead(path, html);
        }
        for (String path : List.of("/settings", "/manage/posts", "/notifications", "/feed")) {
            String html = mockMvc.perform(get(path).with(TestAuth.member(me))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertHead(path, html);
        }
        String csp = mockMvc.perform(get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("script-src 'self'");
        mockMvc.perform(get("/css/theme.css")).andExpect(status().isOk());
        mockMvc.perform(get("/css/site.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/theme-init.js")).andExpect(status().isOk());
    }

    private static void assertHead(String path, String html) {
        String head = html.substring(0, html.indexOf("</head>"));
        assertThat(head).as(path).contains("<meta name=\"color-scheme\" content=\"light dark\">")
                .contains("<script src=\"/js/theme-init.js\"></script>").contains("/css/theme.css");
        assertThat(head.indexOf("theme-init.js")).as(path).isLessThan(head.indexOf("/css/site.css"));
        assertThat(html).as(path).contains("id=\"theme-toggle\" class=\"theme-toggle\" hidden")
                .doesNotContain("<style>").doesNotContainPattern("<script>(?!</script>)");
        assertThat(Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>").matcher(html).find()).as(path + " 인라인 스크립트").isFalse();
    }

    @Test
    void colorsComeOnlyFromRoleFile() throws IOException {
        String theme = Files.readString(Path.of("src/main/resources/static/css/theme.css"));
        assertThat(theme).contains("--color-bg: #121212").contains("prefers-color-scheme: dark").contains(":root[data-theme=\"dark\"]")
                .doesNotContain("transition");
        String site = Files.readString(Path.of("src/main/resources/static/css/site.css"));
        assertThat(HEX.matcher(site).find()).as("site.css에 직접 쓴 색").isFalse();
        assertThat(site).doesNotContain("rgba(").doesNotContain("filter: invert");
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            files.filter(f -> f.toString().endsWith(".html") && !f.toString().contains("/mail/")).forEach(f -> {
                try {
                    String html = Files.readString(f);
                    assertThat(Pattern.compile("(color|background)\\s*:\\s*#").matcher(html).find()).as(f.toString()).isFalse();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/static/js"))) {
            files.filter(f -> f.toString().endsWith(".js")).forEach(f -> {
                try {
                    assertThat(HEX.matcher(Files.readString(f)).find()).as(f.toString()).isFalse();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    /** 코드 문법 강조: highlight.js를 우리 서버에서 내려주고(CSP 그대로), 글 상세·편집 화면이 불러온다. */
    @Test
    void codeHighlightServedFromOwnOrigin() throws Exception {
        mockMvc.perform(get("/webjars/highlightjs__cdn-assets/11.11.1/highlight.min.js")).andExpect(status().isOk());
        String theme = Files.readString(Path.of("src/main/resources/static/css/theme.css"));
        assertThat(theme).contains("--code-keyword").contains("--code-string");
        long author = members.localMember("hlauthor", "hlauthor", "hlauthor@example.com", "Blog#2026ok", true);
        long post = posts.published(author, "코드 글", "본문", 1, java.time.Instant.parse("2026-10-01T00:00:00Z"));
        // 코드 블록이 없는 글은 불러오지 않는다(40 §2)
        String plain = mockMvc.perform(get("/@hlauthor/posts/{id}", post)).andReturn().getResponse().getContentAsString();
        assertThat(plain).doesNotContain("/js/code-highlight.js");
        jdbc.update("UPDATE post SET content_html = '<pre><code class=\"language-java\">int a;</code></pre>' WHERE id = ?", post);
        String html = mockMvc.perform(get("/@hlauthor/posts/{id}", post)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/webjars/highlightjs__cdn-assets/11.11.1/highlight.min.js").contains("/js/code-highlight.js");
    }

    /** 디자인 시안(10-08): 글꼴은 우리 서버에서(CSP font-src 'self'), 사진 없는 카드에는 빈 사진 칸이 없다. */
    @Test
    void fontsServedFromOwnOriginAndCardsSkipEmptyThumb() throws Exception {
        mockMvc.perform(get("/fonts/pretendard/pretendard.css")).andExpect(status().isOk());
        mockMvc.perform(get("/fonts/gaegu/gaegu.css")).andExpect(status().isOk());
        // 새로고침마다 글꼴을 다시 받지 않게 오래 보관
        String cache = mockMvc.perform(get("/fonts/gaegu/files/gaegu-0-400-normal.woff2")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");
        assertThat(cache).contains("max-age=31536000").contains("immutable").doesNotContain("no-store");
        long author = members.localMember("cardauthor", "cardauthor", "cardauthor@example.com", "Blog#2026ok", true);
        posts.published(author, "사진 없는 글", "본문", 1, java.time.Instant.parse("2026-10-01T00:00:00Z"));
        String home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("/fonts/pretendard/pretendard.css").contains("사진 없는 글").doesNotContain("class=\"card-thumb\"");
    }
}
