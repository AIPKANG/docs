package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.PostTitleRules;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** 007 T412: 글 제목 정리(FR-018). */
class PostTitleRulesTest {

    @Test
    void removesInvisibleBidiAndControlCharacters() {
        assertThat(PostTitleRules.clean("안​녕‮txt.exe\u0007")).isEqualTo("안녕txt.exe");
        assertThat(PostTitleRules.clean("﻿제목⁠⁦")).isEqualTo("제목");
        assertThat(PostTitleRules.clean("  앞뒤 공백  ")).isEqualTo("앞뒤 공백");
        assertThat(PostTitleRules.clean("​‌")).isEmpty();
        assertThat(PostTitleRules.clean(null)).isEmpty();
    }

    @Test
    void nfdHangulBecomesNfc() {
        String nfd = Normalizer.normalize("한글 제목", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("한글 제목");
        assertThat(PostTitleRules.clean(nfd)).isEqualTo("한글 제목");
    }

    @Test
    void htmlIsKeptAsTextForEscapingOnDisplay() {
        assertThat(PostTitleRules.clean("<script>alert(1)</script>")).isEqualTo("<script>alert(1)</script>");
    }
}
