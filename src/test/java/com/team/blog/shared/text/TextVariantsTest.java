package com.team.blog.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextVariantsTest {

    @Test
    void producesFourVariantsInOrder() {
        assertThat(TextVariants.of("sh1t")).containsExactly("sh1t", "sht", "shit", "shlt");
        assertThat(TextVariants.of("f4ck")).containsExactly("f4ck", "fck", "fack", "f4ck");
        assertThat(TextVariants.of("시1발")).containsExactly("시1발", "시발", "시i발", "시l발");
    }

    @Test
    void mapsAllLeetDigits() {
        assertThat(TextVariants.of("0134572689").get(2)).isEqualTo("oieast2689");
    }

    @Test
    void textWithoutDigitsIsUnchangedInEveryVariant() {
        assertThat(TextVariants.of("kim")).containsExactly("kim", "kim", "kim", "kim");
    }
}
