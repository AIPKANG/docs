package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.PostContentRules;
import org.junit.jupiter.api.Test;

/** 004 T304: 저장 전 제목·본문 규칙(research R-7). */
class PostContentRulesTest {

    @Test
    void titleControlCharactersBecomeSpacesAndNullBecomesEmpty() {
        assertThat(PostContentRules.normalizeTitle("a\nb\tc\u0000d")).isEqualTo("a b c d");
        assertThat(PostContentRules.normalizeTitle(null)).isEmpty();
        assertThat(PostContentRules.normalizeTitle("  그대로  ")).isEqualTo("  그대로  ");
    }

    @Test
    void contentLineBreaksAreNormalizedAndMarkdownKept() {
        assertThat(PostContentRules.normalizeContent("a\r\nb\rc")).isEqualTo("a\nb\nc");
        assertThat(PostContentRules.normalizeContent("<script>x</script>")).isEqualTo("<script>x</script>");
        assertThat(PostContentRules.normalizeContent(null)).isEmpty();
    }

    @Test
    void lengthsCountCodePoints() {
        assertThat(PostContentRules.firstViolation("😀".repeat(100), "", 100, 10)).isEmpty();
        assertThat(PostContentRules.firstViolation("😀".repeat(101), "", 100, 10)).contains("TITLE_TOO_LONG");
        assertThat(PostContentRules.firstViolation("", "a".repeat(11), 100, 10)).contains("CONTENT_TOO_LONG");
    }

    @Test
    void blankMeansWhitespaceOnly() {
        assertThat(PostContentRules.isBlank(" ", "\n\t")).isTrue();
        assertThat(PostContentRules.isBlank("", "x")).isFalse();
    }
}
