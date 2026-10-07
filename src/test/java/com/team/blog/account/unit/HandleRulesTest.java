package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.HandlePrefix;
import com.team.blog.account.domain.HandleRules;
import com.team.blog.account.domain.Provider;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HandleRulesTest {

    /** 08 §3 예시를 재현하는 고정 난수원. */
    static final RandomGenerator FIXED = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 483920;
        }

        @Override
        public int nextInt(int bound) {
            return 483920 % bound;
        }
    };

    static String prefill(String email, Provider provider) {
        return Handle.of(HandlePrefix.of(provider), HandleRules.bodyFromEmail(email, FIXED)).toString();
    }

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource({
            "kim755030@naver.com, LOCAL, kim755030",
            "kim755030@gmail.com, GOOGLE, go-kim755030",
            "kim755030@naver.com, GITHUB, gi-kim755030",
            "gokim@naver.com, LOCAL, gokim",
            "Kim.Min-Seo+blog@naver.com, LOCAL, kim_min_seo",
            "_kim__min_@x.com, LOCAL, kim_min",
            "admin@x.com, LOCAL, admin",
            "ab@x.com, LOCAL, user_483920",
            "김민서@한국.kr, GOOGLE, go-user_483920",
            "김민서@한국.kr, LOCAL, user_483920",
            "12345678+octocat@users.noreply.github.com, GITHUB, gi-12345678",
    })
    void steps1to9MatchDocExamples(String email, Provider provider, String expected) {
        assertThat(prefill(email, provider)).isEqualTo(expected);
    }

    @Test
    void usesLastAtSign() {
        assertThat(HandleRules.bodyFromEmail("\"a@b\"kim@x.com", FIXED)).isEqualTo("abkim");
    }

    @Test
    void cutsTo30AndDropsTrailingUnderscore() {
        String local = "abcdefghijklmnopqrstuvwxyz012.345";  // 29자 뒤 '_' (30번째)
        String body = HandleRules.bodyFromEmail(local + "@x.com", FIXED);
        assertThat(body).isEqualTo("abcdefghijklmnopqrstuvwxyz012");
        assertThat(HandleRules.bodyFromEmail("a".repeat(40) + "@x.com", FIXED)).hasSize(30);
    }

    @Test
    void missingEmailStartsAtRandomStep() {
        assertThat(HandleRules.bodyFromEmail(null, FIXED)).isEqualTo("user_483920");
        RandomGenerator zero = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 7;
            }
        };
        assertThat(HandleRules.bodyFromEmail("", zero)).isEqualTo("user_000007");
    }

    @Test
    void numberSuffixKeepsBodyWithin36() {
        assertThat(HandleRules.withNumber("kim755030", 2)).isEqualTo("kim755030_2");
        String long36 = "a".repeat(36);
        assertThat(HandleRules.withNumber(long36, 2)).isEqualTo("a".repeat(34) + "_2").hasSize(36);
        String endsWithUnderscoreAfterCut = "a".repeat(33) + "_bc";
        assertThat(HandleRules.withNumber(endsWithUnderscoreAfterCut, 2)).isEqualTo("a".repeat(33) + "_2");
        assertThat(HandleRules.withNumber(long36, 12345)).hasSize(36);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "kim, true",
            "go-kim, true",
            "gi-kim_min, true",
            "kim_min_seo, true",
            "ab, false",
            "kim-min, false",
            "_kim, false",
            "kim_, false",
            "Kim, false",
            "go-ab, false",
            "xx-kim, false",
            "go-go-kim, false",
            "kim.min, false",
    })
    void formatTable(String handle, boolean valid) {
        assertThat(HandleRules.isValidFormat(handle)).isEqualTo(valid);
    }

    @Test
    void formatLengthBoundaries() {
        assertThat(HandleRules.isValidFormat("a".repeat(36))).isTrue();
        assertThat(HandleRules.isValidFormat("a".repeat(37))).isFalse();
        assertThat(HandleRules.isValidFormat("go-" + "a".repeat(36))).isTrue();
        assertThat(("go-" + "a".repeat(36))).hasSize(39);
        assertThat(HandleRules.FORMAT).isEqualTo("^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$");
    }

    @Test
    void normalizeInputTrimsAndLowercases() {
        assertThat(HandleRules.normalizeInput("  KimMin  ")).isEqualTo("kimmin");
    }
}
