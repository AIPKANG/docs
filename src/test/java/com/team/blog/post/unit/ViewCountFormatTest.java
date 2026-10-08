package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.ViewCountFormat;
import org.junit.jupiter.api.Test;

/** 010 T1001: 조회 수 표시(FR-012). */
class ViewCountFormatTest {

    @Test
    void formats() {
        assertThat(ViewCountFormat.format(0)).isEqualTo("0");
        assertThat(ViewCountFormat.format(1234)).isEqualTo("1,234");
        assertThat(ViewCountFormat.format(9999)).isEqualTo("9,999");
        assertThat(ViewCountFormat.format(10_000)).isEqualTo("1만");
        assertThat(ViewCountFormat.format(12_345)).isEqualTo("1.2만");
        assertThat(ViewCountFormat.format(19_999)).isEqualTo("1.9만");
        assertThat(ViewCountFormat.format(1_234_567)).isEqualTo("123.4만");
    }
}
