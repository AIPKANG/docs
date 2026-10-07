package com.team.blog.account.domain;

import java.util.regex.Pattern;

/** 블로그 주소 순수 규칙. 형식 정규식은 DB {@code ck_member_handle}과 같은 문자열 상수다(설정 아님). */
public final class HandleRules {

    /** DB {@code ck_member_handle}과 같은 문자열. 바꾸려면 Flyway 마이그레이션이 함께 필요하다. */
    public static final String FORMAT = "^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$";

    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT);

    private HandleRules() {
    }

    public static boolean isValidFormat(String handle) {
        return handle != null && FORMAT_PATTERN.matcher(handle).matches();
    }
}
