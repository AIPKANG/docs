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
}
