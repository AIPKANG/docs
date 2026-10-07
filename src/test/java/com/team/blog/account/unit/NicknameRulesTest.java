package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.NicknameRules;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.shared.text.BannedWordFilter;
import com.team.blog.shared.text.WordListLoader;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameRulesTest {

    /** FR-017 15개. */
    static final List<String> RESERVED = List.of("관리자", "운영자", "운영진", "운영팀", "고객센터", "공식", "매니저", "스태프",
                    "admin", "administrator", "official", "staff", "manager", "system", "root").stream()
            .map(WordListLoader::normalize).toList();

    /** 테스트 목록 + 1→l 변형 확인용 단어. */
    static final BannedWordFilter FILTER = BannedWordFilter.of(
            Set.of("시발", "씨발", "병신", "shit", "fack", "lol"), Set.of("시발점", "시발역"));

    static Optional<NicknameViolation> check(String raw) {
        return NicknameRules.firstViolation(NicknameRules.normalize(raw), RESERVED, FILTER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ㅋㅋ", "김 민서", "kim!", "😀kim", "김", "가나다라마바사아자차카", "", "kim​min", "kim‮min"})
    void invalidFormat(String raw) {
        assertThat(check(raw)).contains(NicknameViolation.NICKNAME_INVALID_FORMAT);
    }

    @Test
    void letterRequired() {
        assertThat(check("12345")).contains(NicknameViolation.NICKNAME_LETTER_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"관리자김", "admin123", "Official", "운영팀장", "adm1n", "ROOTkim"})
    void reserved(String raw) {
        assertThat(check(raw)).contains(NicknameViolation.NICKNAME_RESERVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시1발", "sh1t", "병1신왕", "1o1kim"})
    void bannedWord(String raw) {
        assertThat(check(raw)).contains(NicknameViolation.NICKNAME_BANNED_WORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시발점", "김민서", "Kim", "KIM2", "  김민서  "})
    void passes(String raw) {
        assertThat(check(raw)).isEmpty();
    }

    @Test
    void normalizesToNfc() {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        assertThat(nfd).hasSizeGreaterThan(3);
        String normalized = NicknameRules.normalize(nfd);
        assertThat(normalized).isEqualTo("김민서").hasSize(3);
        assertThat(check(nfd)).isEmpty();
    }

    @Test
    void stopsAtFirstFailureInOrder() {
        // 형식 위반이면서 예약어·금칙어도 포함 → 형식이 먼저
        assertThat(check("admin 시발")).contains(NicknameViolation.NICKNAME_INVALID_FORMAT);
        // 예약어이면서 금칙어 → 예약어가 먼저
        assertThat(check("admin시발")).contains(NicknameViolation.NICKNAME_RESERVED);
    }

    @ParameterizedTest(name = "{0} -> [{1}]")
    @CsvSource(value = {
            "Kim Min-seo|KimMinseo",
            "김민서 (Minseo)|김민서Minseo",
            "Christopher Columbus|Christophe",
            "A|A",
    }, delimiter = '|')
    void cleansSocialNames(String displayName, String expected) {
        assertThat(NicknameRules.cleanSocialName(displayName)).isEqualTo(expected);
    }

    @Test
    void socialCandidateIsEmptyWhenRulesFail() {
        assertThat(NicknameRules.socialNameCandidate("A", RESERVED, FILTER)).isEmpty();
        assertThat(NicknameRules.socialNameCandidate("Kim Min-seo", RESERVED, FILTER)).contains("KimMinseo");
        assertThat(NicknameRules.socialNameCandidate("Admin Kim", RESERVED, FILTER)).isEmpty();
    }

    @Test
    void dbPatternsAreTheSameStrings() {
        assertThat(NicknameRules.FORMAT).isEqualTo("^[가-힣a-zA-Z0-9]{2,10}$");
        assertThat(NicknameRules.LETTER).isEqualTo("[가-힣a-zA-Z]");
    }
}
