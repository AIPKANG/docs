package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.HandleCheckResult;
import com.team.blog.account.application.HandleService;
import com.team.blog.account.domain.HandleRules;
import com.team.blog.account.domain.HandleViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.support.IntegrationTestBase;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HandleSignupIT extends IntegrationTestBase {

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

    @Autowired
    HandleService handleService;

    private int nicknameSeq = 0;

    private void signUpWith(String handle) {
        members.active(handle, "닉네임" + (char) ('가' + nicknameSeq++));
    }

    @Test
    void prefillReproducesTheTwelveDocExamplesInOrder() {
        record Row(String email, Provider provider, String expected) {
        }
        List<Row> rows = List.of(
                new Row("kim755030@naver.com", Provider.LOCAL, "kim755030"),
                new Row("kim755030@daum.net", Provider.LOCAL, "kim755030_2"),
                new Row("kim755030@gmail.com", Provider.GOOGLE, "go-kim755030"),
                new Row("kim755030@naver.com", Provider.GITHUB, "gi-kim755030"),
                new Row("kim755030@gmail.com", Provider.LOCAL, "kim755030_3"),
                new Row("gokim@naver.com", Provider.LOCAL, "gokim"),
                new Row("Kim.Min-Seo+blog@naver.com", Provider.LOCAL, "kim_min_seo"),
                new Row("_kim__min_@x.com", Provider.LOCAL, "kim_min"),
                new Row("admin@x.com", Provider.LOCAL, "admin_2"),
                new Row("ab@x.com", Provider.LOCAL, "user_483920"),
                new Row("김민서@한국.kr", Provider.GOOGLE, "go-user_483920"),
                new Row("12345678+octocat@users.noreply.github.com", Provider.GITHUB, "gi-12345678"));
        for (Row row : rows) {
            String handle = handleService.prefill(row.email(), row.provider(), FIXED);
            assertThat(handle).as(row.email() + " / " + row.provider()).isEqualTo(row.expected());
            signUpWith(handle);
        }
    }

    @Test
    void prefixMustMatchProvider() {
        assertViolation("go-kim", Provider.LOCAL, HandleViolation.HANDLE_PREFIX_MISMATCH, null);
        assertViolation("gi-kim", Provider.GOOGLE, HandleViolation.HANDLE_PREFIX_MISMATCH, null);
        assertThat(handleService.validateForSignup("kim", Provider.GOOGLE).toString()).isEqualTo("go-kim");
        assertThat(handleService.validateForSignup("go-kim", Provider.GOOGLE).toString()).isEqualTo("go-kim");
        assertThat(handleService.validateForSignup("kim", Provider.GITHUB).toString()).isEqualTo("gi-kim");
    }

    @Test
    void formatReservedBannedDuplicate() {
        assertViolation("admin", Provider.LOCAL, HandleViolation.HANDLE_RESERVED, "admin_2");
        assertViolation("kim-min", Provider.LOCAL, HandleViolation.HANDLE_INVALID_FORMAT, null);
        assertViolation("ab", Provider.LOCAL, HandleViolation.HANDLE_INVALID_FORMAT, null);
        assertViolation("sh1t_blog", Provider.LOCAL, HandleViolation.HANDLE_BANNED_WORD, null);
        assertViolation("sh_it", Provider.LOCAL, HandleViolation.HANDLE_BANNED_WORD, null);
        signUpWith("kim755030");
        signUpWith("kim755030_2");
        assertViolation("kim755030", Provider.LOCAL, HandleViolation.HANDLE_DUPLICATE, "kim755030_3");
    }

    @Test
    void bannedWordIsNeverInTheException() {
        assertThatThrownBy(() -> handleService.validateForSignup("sh1tkim", Provider.LOCAL))
                .isInstanceOfSatisfying(HandleViolationException.class, e -> {
                    assertThat(e.getMessage()).doesNotContainIgnoringCase("shit").doesNotContainIgnoringCase("sh1t");
                    assertThat(e.toString()).doesNotContainIgnoringCase("shit").doesNotContainIgnoringCase("sh1t");
                    assertThat(e.getSuggestion()).isNull();
                });
    }

    @Test
    void uppercaseAndSurroundingSpacesAreNormalized() {
        assertThat(handleService.validateForSignup("  KimMin  ", Provider.LOCAL).toString()).isEqualTo("kimmin");
    }

    @Test
    void numberedSuggestionKeeps36CharBody() {
        String body36 = "a".repeat(36);
        signUpWith(body36);
        assertThatThrownBy(() -> handleService.validateForSignup(body36, Provider.LOCAL))
                .isInstanceOfSatisfying(HandleViolationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(HandleViolation.HANDLE_DUPLICATE);
                    assertThat(e.getSuggestion()).isEqualTo("a".repeat(34) + "_2").hasSize(36);
                    assertThat(HandleRules.isValidFormat(e.getSuggestion())).isTrue();
                });
    }

    @Test
    void availabilityIgnoresProviderPrefixMatching() {
        assertThat(handleService.checkAvailability("go-kim")).isEqualTo(new HandleCheckResult(true, null, null));
        assertThat(handleService.checkAvailability("kim-min").reason()).isEqualTo("INVALID_FORMAT");
    }

    private void assertViolation(String raw, Provider provider, HandleViolation code, String suggestion) {
        assertThatThrownBy(() -> handleService.validateForSignup(raw, provider))
                .as(raw)
                .isInstanceOfSatisfying(HandleViolationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(code);
                    assertThat(e.getSuggestion()).isEqualTo(suggestion);
                });
    }
}
