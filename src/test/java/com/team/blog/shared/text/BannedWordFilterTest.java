package com.team.blog.shared.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

class BannedWordFilterTest {

    /** quickstart §1 테스트 목록. */
    static final BannedWordFilter FILTER = BannedWordFilter.of(
            Set.of("시발", "씨발", "병신", "shit", "fack"), Set.of("시발점", "시발역"));

    @ParameterizedTest
    @ValueSource(strings = {"시발", "시1발", "sh1t", "SH1T", "병1신왕", "f4ck", "나는병신", "씨발놈"})
    void blocksBannedWordsAndVariants(String input) {
        assertThat(FILTER.containsBanned(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"시발점", "시발역", "김민서", "kim", "shift", "시발점에서"})
    void allowsExceptionsAndCleanWords(String input) {
        assertThat(FILTER.containsBanned(input)).isFalse();
    }

    @Test
    void exceptionRemovalDoesNotHideOtherBannedWords() {
        assertThat(FILTER.containsBanned("시발점병신")).isTrue();
    }

    @Test
    void exceptionsAreRemovedLongestFirstThenLeftmost() {
        BannedWordFilter filter = BannedWordFilter.of(Set.of("ab"), Set.of("xab", "xabc"));
        assertThat(filter.containsBanned("xabc")).isFalse();
        assertThat(filter.containsBanned("xab")).isFalse();
        assertThat(filter.containsBanned("xabab")).isTrue();
    }

    @Test
    void oneToLVariantIsChecked() {
        BannedWordFilter filter = BannedWordFilter.of(Set.of("lol"), Set.of("zzz"));
        assertThat(filter.containsBanned("1o1")).isTrue();
    }

    @Test
    void handleBodyAlsoChecksUnderscoreRemovedVariant() {
        assertThat(FILTER.containsBanned("sh_it")).isFalse();
        assertThat(FILTER.containsBannedInHandleBody("sh_it")).isTrue();
        assertThat(FILTER.containsBannedInHandleBody("kim_min")).isFalse();
    }

    @Test
    void publicApiReturnsOnlyBooleans() {
        for (Method method : BannedWordFilter.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("contains")) {
                assertThat(method.getReturnType()).isEqualTo(boolean.class);
            }
        }
        assertThat(Arrays.stream(BannedWordFilter.class.getMethods()).map(Method::getReturnType))
                .doesNotContain((Class) String.class.arrayType());
    }

    @Test
    void startupFailsWhenListMissingOrEmpty() {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        assertThatThrownBy(() -> new BannedWordFilter(loader, "classpath:policy/none.txt",
                "classpath:policy/test-banned-words-exceptions.txt"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> WordListLoader.load(new ByteArrayResource("# only comment\n\n".getBytes())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loaderNormalizesToNfcLowercase() {
        String nfd = java.text.Normalizer.normalize("시발", java.text.Normalizer.Form.NFD);
        Set<String> words = WordListLoader.load(new ByteArrayResource(("# c\n" + nfd + "\nSHIT\n").getBytes()));
        assertThat(words).containsExactlyInAnyOrder("시발", "shit");
    }
}
