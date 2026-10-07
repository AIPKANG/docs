package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.shared.text.BannedWordFilter;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.domain.TagRejectedException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 005 T502: 22 §2-1 예시. */
class TagNormalizerTest {

    private final TagNormalizer normalizer = new TagNormalizer(BannedWordFilter.of(Set.of("나쁜말"), Set.of()));

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Spring Boot|spring-boot",
        "#JPA|jpa",
        "'  C++ '|c++",
        "C#|c#",
        "Node.JS|node.js",
        ".NET|.net",
        "ｓｐｒｉｎｇ|spring",
        "스프링  부트|스프링-부트",
        "spring--boot|spring-boot",
        "자바_기초|자바_기초",
        "##jpa|jpa",
        "'-spring-boot-'|spring-boot",
        "Spr\u200Bing|spring"
    })
    void normalizes(String raw, String expected) {
        assertThat(normalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"...", "---", "#", "ㅋㅋ", "ㅅㅂ", "🔥hot", "a/b", "c@d", "", "   "})
    void invalid(String raw) {
        assertThatThrownBy(() -> normalizer.normalize(raw)).isInstanceOf(TagRejectedException.class)
                .extracting("code").isEqualTo("INVALID_TAG");
    }

    @Test
    void tooLongAndBanned() {
        assertThat(normalizer.normalize("a".repeat(30))).hasSize(30);
        assertThatThrownBy(() -> normalizer.normalize("a".repeat(31))).extracting("code").isEqualTo("TAG_TOO_LONG");
        assertThatThrownBy(() -> normalizer.normalize("진짜나쁜말")).extracting("code").isEqualTo("TAG_BANNED_WORD");
    }
}
