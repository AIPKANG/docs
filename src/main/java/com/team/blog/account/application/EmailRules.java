package com.team.blog.account.application;

import java.util.Locale;
import java.util.regex.Pattern;

/** 이메일 정규화·형식 검사(FR-004, FR-005): trim + 소문자, 일반 형식, 최대 254자. */
public final class EmailRules {

    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private EmailRules() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** @return 정규화한 이메일 @throws InvalidEmailException 형식·길이 위반 */
    public static String normalizeAndValidate(String raw) {
        String email = normalize(raw);
        if (email.isEmpty() || email.length() > MAX_LENGTH || !FORMAT.matcher(email).matches()) {
            throw new InvalidEmailException();
        }
        return email;
    }
}
