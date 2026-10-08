package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.tag.application.suggest.AiInputCleaner;
import com.team.blog.tag.application.suggest.TrigramSimilarity;
import org.junit.jupiter.api.Test;

/** 021 FR-012·FR-013·FR-022. */
class AiInputCleanerTest {

    @Test
    void stripsMarkdownKeepsTextAndFirstFiveCodeLines() {
        String md = """
                # 제목 기호
                > 인용 **강조** 와 _보통_ 글
                - 목록 [링크 글자](https://example.com/x) ![그림](https://img/x.png)
                1. 번호 목록

                ```java
                line1
                line2
                line3
                line4
                line5
                line6
                ```
                끝  문장
                """;
        String cleaned = AiInputCleaner.clean("글 제목", md);
        assertThat(cleaned).startsWith("글 제목 제목 기호 인용 강조 와 _보통_ 글 목록 링크 글자 번호 목록 java line1")
                .contains("line5").doesNotContain("line6").doesNotContain("https").doesNotContain("그림").doesNotContain("#")
                .endsWith("끝 문장");
        assertThat(AiInputCleaner.clean("A", "Spring")).isEqualTo("A Spring");
        assertThat(AiInputCleaner.truncate("가나다라", 2)).isEqualTo("가나");
    }

    @Test
    void trigramJaccard() {
        String a = "스프링 부트에서 JPA 트랜잭션을 다루는 방법을 정리했다. ".repeat(6);
        assertThat(TrigramSimilarity.jaccard(a, a)).isEqualTo(1.0);
        assertThat(TrigramSimilarity.jaccard(a, a.replaceFirst("정리했다", "정리햇다"))).isGreaterThanOrEqualTo(0.9);
        assertThat(TrigramSimilarity.jaccard(a, "전혀 다른 글입니다 무엇이든")).isLessThan(0.2);
    }
}
