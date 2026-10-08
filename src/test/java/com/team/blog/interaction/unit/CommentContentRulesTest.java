package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.CommentContentRules;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** 014 T1401: 댓글 내용 정리(FR-008·FR-009). */
class CommentContentRulesTest {

    @Test
    void cleansButKeepsLineBreaks() {
        assertThat(CommentContentRules.clean("  첫 줄\r\n\r\n\r\n\n둘째​ 줄‮\u0007  ")).isEqualTo("첫 줄\n\n둘째 줄");
        assertThat(CommentContentRules.clean(Normalizer.normalize("한글", Normalizer.Form.NFD))).isEqualTo("한글");
        assertThat(CommentContentRules.clean("<b>굵게</b> **md** https://x.dev")).isEqualTo("<b>굵게</b> **md** https://x.dev");
        assertThat(CommentContentRules.clean("a\n   \n\n  \nb")).isEqualTo("a\n\nb");
    }

    @Test
    void lengthInCodePoints() {
        assertThat(CommentContentRules.violation("", 1000)).contains("COMMENT_REQUIRED");
        assertThat(CommentContentRules.violation("😀".repeat(1000), 1000)).isEmpty();
        assertThat(CommentContentRules.violation("😀".repeat(1001), 1000)).contains("COMMENT_TOO_LONG");
    }
}
