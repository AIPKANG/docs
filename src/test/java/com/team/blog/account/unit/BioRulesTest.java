package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.BioRules;
import com.team.blog.account.domain.BioViolation;
import com.team.blog.shared.text.BannedWordFilter;
import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 003 T221: 소개 정리·검사(11 §3, research R-3). */
class BioRulesTest {

    private static final BannedWordFilter FILTER = BannedWordFilter.of(Set.of("시발", "shit"), Set.of("시발점"));

    private static Optional<BioViolation> check(String raw) {
        return BioRules.firstViolation(BioRules.normalize(raw), 200, 4, FILTER);
    }

    @Test
    void lengthIsCountedInCodePoints() {
        assertThat(check("가".repeat(200))).isEmpty();
        assertThat(check("가".repeat(201))).contains(BioViolation.BIO_TOO_LONG);
        // 이모지(보조 평면) 200개는 200자
        assertThat(check("😀".repeat(200))).isEmpty();
        assertThat(check("😀".repeat(201))).contains(BioViolation.BIO_TOO_LONG);
        assertThat(BioRules.codePointLength("👨‍👩‍👧")).isEqualTo(5);
    }

    @Test
    void trimsAndNormalizesToNfc() {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        assertThat(BioRules.normalize("  " + nfd + "  \n ")).isEqualTo("김민서");
        assertThat(BioRules.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc");
        assertThat(BioRules.normalize("끝 공백   \n다음")).isEqualTo("끝 공백\n다음");
    }

    @Test
    void consecutiveBlankLinesCollapseBeforeCounting() {
        assertThat(BioRules.normalize("a\n\n\n\nb")).isEqualTo("a\n\nb");
        assertThat(BioRules.lineCount("a\n\nb")).isEqualTo(3);
        assertThat(check("a\n\n\n\n\n\nb\nc")).isEmpty();
        assertThat(check("1\n2\n3\n4")).isEmpty();
        assertThat(check("1\n2\n3\n4\n5")).contains(BioViolation.BIO_TOO_MANY_LINES);
        assertThat(check("1\n \n \n2")).isEmpty();
    }

    @Test
    void invisibleBidiAndControlCharactersAreRemovedButEmojiJoinersKept() {
        assertThat(BioRules.normalize("abc‮evil")).isEqualTo("abcevil");
        assertThat(BioRules.normalize("a​b﻿c⁦d")).isEqualTo("abcd");
        assertThat(BioRules.normalize("a\u0007b\u0000c")).isEqualTo("abc");
        assertThat(BioRules.normalize("a\tb")).isEqualTo("a b");
        assertThat(BioRules.normalize("가족 👨‍👩‍👧")).isEqualTo("가족 👨‍👩‍👧");
        assertThat(BioRules.normalize("하트 ❤️")).isEqualTo("하트 ❤️");
    }

    @Test
    void bannedWordsUseNicknameFilterWithExceptions() {
        assertThat(check("오늘은 시1발")).contains(BioViolation.BIO_BANNED_WORD);
        assertThat(check("this is sh1t")).contains(BioViolation.BIO_BANNED_WORD);
        assertThat(check("시발점에서 출발해요")).isEmpty();
    }

    @Test
    void emptyIsAllowedAndHtmlIsJustText() {
        assertThat(BioRules.normalize("   ")).isEmpty();
        assertThat(check("")).isEmpty();
        assertThat(check("<script>alert(1)</script> https://example.com")).isEmpty();
    }

    @Test
    void lengthIsCheckedBeforeLinesAndBannedWords() {
        assertThat(check("시발\n".repeat(150))).contains(BioViolation.BIO_TOO_LONG);
        assertThat(check("시발\n1\n2\n3\n4")).contains(BioViolation.BIO_TOO_MANY_LINES);
    }
}
