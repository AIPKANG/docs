package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.SearchTerms;
import org.junit.jupiter.api.Test;

/** 020 FR-003·FR-004·FR-007. */
class SearchTermsTest {

    @Test
    void normalizesSplitsAndLimits() {
        SearchTerms t = SearchTerms.parse("  트랜잭션  정리 a  ");
        assertThat(t.words()).containsExactly("트랜잭션", "정리");
        assertThat(t.hasTwoCharWord()).isTrue();
        assertThat(SearchTerms.parse("aa bb cc dd ee ff gg").words()).hasSize(5);
        assertThat(SearchTerms.parse("가".repeat(60)).text()).hasSize(50);
        // NFD 한글 → NFC
        assertThat(SearchTerms.parse("가나다").words()).containsExactly("가나다");
        assertThat(SearchTerms.likePattern("100%_\\")).isEqualTo("%100\\%\\_\\\\%");
    }
}
