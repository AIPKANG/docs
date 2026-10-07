package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.PasswordPolicy;
import com.team.blog.account.domain.PasswordPolicyViolation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** 001 T120: 비밀번호 규칙(FR-012, FR-013, SC-003). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(List.of("Password1!", "Qwer1234!", "# 주석"), 3);

    private List<PasswordPolicyViolation> check(String password) {
        return policy.validate(password, "kim755030@naver.com");
    }

    @Test
    void validPasswordHasNoViolation() {
        assertThat(check("Blog#2026ok")).isEmpty();
        assertThat(check("a1!aaaaa")).isEmpty();
        assertThat(check("Z9~" + "x".repeat(13))).isEmpty(); // 16자
    }

    @Test
    void eachRuleIsEnforced() {
        assertThat(check("Ab1!")).contains(PasswordPolicyViolation.TOO_SHORT);
        assertThat(check("Abcdefgh1!" + "abcdefg")).containsExactly(PasswordPolicyViolation.TOO_LONG); // 17자
        assertThat(check("12345678!")).containsExactly(PasswordPolicyViolation.LETTER_REQUIRED);
        assertThat(check("abcdefgh!")).containsExactly(PasswordPolicyViolation.DIGIT_REQUIRED);
        assertThat(check("abcdefg12")).containsExactly(PasswordPolicyViolation.SPECIAL_REQUIRED);
        assertThat(check("abc def1!")).containsExactly(PasswordPolicyViolation.INVALID_CHARACTER);
        assertThat(check("abc한글12!")).containsExactly(PasswordPolicyViolation.INVALID_CHARACTER);
        assertThat(check("xKIM755030!")).containsExactly(PasswordPolicyViolation.CONTAINS_EMAIL_LOCAL_PART);
        assertThat(check("Password1!")).containsExactly(PasswordPolicyViolation.TOO_COMMON);
        assertThat(check("qwer1234!")).containsExactly(PasswordPolicyViolation.TOO_COMMON); // 대소문자 무시
    }

    @Test
    void onlyTheListedSpecialCharactersAreAllowed() {
        for (char c : PasswordPolicy.SPECIALS.toCharArray()) {
            assertThat(check("abcd123" + c)).as("allowed " + c).isEmpty();
        }
        for (String c : List.of("€", "·", "\t", "é", "＃")) {
            assertThat(check("abcd123!" + c)).as("not allowed " + c).contains(PasswordPolicyViolation.INVALID_CHARACTER);
        }
    }

    @Test
    void shortEmailLocalPartIsNotChecked() {
        assertThat(policy.validate("ab12345!", "ab@x.com")).isEmpty();
        assertThat(policy.validate("abc12345!", "abc@x.com")).containsExactly(PasswordPolicyViolation.CONTAINS_EMAIL_LOCAL_PART);
        assertThat(policy.validate("abc12345!", null)).isEmpty();
    }

    @Test
    void tooLongMessageSaysMaximumSixteen() throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/messages.properties")) {
            messages.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(messages.getProperty("password.TOO_LONG")).contains("최대 16자");
        assertThat(messages.getProperty("password.TOO_SHORT")).contains("최대 16자");
        assertThat(Arrays.stream(PasswordPolicyViolation.values()))
                .allMatch(v -> messages.getProperty("password." + v.name()) != null);
    }

    @Test
    void bundledCommonPasswordListIsUsable() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/security/common-passwords.txt")) {
            List<String> lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
            PasswordPolicy bundled = new PasswordPolicy(lines, 3);
            assertThat(bundled.validate("Password1!", null)).containsExactly(PasswordPolicyViolation.TOO_COMMON);
            assertThat(bundled.validate("QWER1234!", null)).containsExactly(PasswordPolicyViolation.TOO_COMMON);
        }
    }
}
