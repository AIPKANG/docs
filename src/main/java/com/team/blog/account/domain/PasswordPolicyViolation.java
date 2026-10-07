package com.team.blog.account.domain;

/**
 * 비밀번호 규칙 위반 종류(FR-012, FR-013). 화면 문구는 {@code messages.properties}의 {@code password.{이름}}.
 * {@link #TOO_LONG} 문구에는 "최대 16자"가 들어간다(SC-003, FR-015).
 */
public enum PasswordPolicyViolation {
    TOO_SHORT,
    TOO_LONG,
    LETTER_REQUIRED,
    DIGIT_REQUIRED,
    SPECIAL_REQUIRED,
    INVALID_CHARACTER,
    CONTAINS_EMAIL_LOCAL_PART,
    TOO_COMMON
}
