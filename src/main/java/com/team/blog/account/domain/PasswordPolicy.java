package com.team.blog.account.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 비밀번호 규칙 한곳(research R-4, 07 §4). 가입·재설정·변경(003)이 같은 정책을 쓴다. 순수 함수 — 목록과 수치는 생성자로 받는다.
 * <ol>
 *   <li>8~16자</li>
 *   <li>영문(대소문자 중) 1+, 숫자 1+, 특수문자 1+</li>
 *   <li>허용 문자 = 영문·숫자·{@link #SPECIALS}(공백·한글·그 밖의 문자 불가)</li>
 *   <li>이메일 {@code @} 앞부분 포함 금지(앞부분이 {@code localPartMinLength}자 이상일 때, 대소문자 무시)</li>
 *   <li>흔한 비밀번호 목록 금지(대소문자 무시)</li>
 * </ol>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 16;

    /** 07 §4 허용 특수문자. */
    public static final String SPECIALS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

    private final Set<String> commonPasswordsLower;
    private final int localPartMinLength;

    public PasswordPolicy(Collection<String> commonPasswords, int localPartMinLength) {
        Set<String> lower = new HashSet<>();
        for (String p : commonPasswords) {
            String v = p.strip();
            if (!v.isEmpty() && !v.startsWith("#")) {
                lower.add(v.toLowerCase(Locale.ROOT));
            }
        }
        this.commonPasswordsLower = Set.copyOf(lower);
        this.localPartMinLength = localPartMinLength;
    }

    /** @return 위반 목록(없으면 빈 목록). 원문을 저장·로그하지 않는다. */
    public List<PasswordPolicyViolation> validate(String raw, String email) {
        List<PasswordPolicyViolation> violations = new ArrayList<>();
        String password = raw == null ? "" : raw;
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            violations.add(PasswordPolicyViolation.TOO_LONG);
        }
        boolean letter = false;
        boolean digit = false;
        boolean special = false;
        boolean invalid = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letter = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else if (SPECIALS.indexOf(c) >= 0) {
                special = true;
            } else {
                invalid = true;
            }
        }
        if (!letter) {
            violations.add(PasswordPolicyViolation.LETTER_REQUIRED);
        }
        if (!digit) {
            violations.add(PasswordPolicyViolation.DIGIT_REQUIRED);
        }
        if (!special) {
            violations.add(PasswordPolicyViolation.SPECIAL_REQUIRED);
        }
        if (invalid) {
            violations.add(PasswordPolicyViolation.INVALID_CHARACTER);
        }
        String localPart = localPart(email);
        if (localPart != null && localPart.length() >= localPartMinLength
                && password.toLowerCase(Locale.ROOT).contains(localPart)) {
            violations.add(PasswordPolicyViolation.CONTAINS_EMAIL_LOCAL_PART);
        }
        if (commonPasswordsLower.contains(password.toLowerCase(Locale.ROOT))) {
            violations.add(PasswordPolicyViolation.TOO_COMMON);
        }
        return List.copyOf(violations);
    }

    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        String value = email.strip().toLowerCase(Locale.ROOT);
        int at = value.lastIndexOf('@');
        if (at <= 0) {
            return null;
        }
        return value.substring(0, at);
    }
}
