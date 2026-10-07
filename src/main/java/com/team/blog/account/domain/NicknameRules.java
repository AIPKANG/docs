package com.team.blog.account.domain;

import java.util.regex.Pattern;

/** 닉네임 순수 규칙. 형식·글자 포함 정규식은 DB {@code ck_member_nickname}과 같은 문자열 상수다. */
public final class NicknameRules {

    /** DB {@code ck_member_nickname} 첫 조건과 같은 문자열. */
    public static final String FORMAT = "^[가-힣a-zA-Z0-9]{2,10}$";

    /** DB {@code ck_member_nickname} 둘째 조건과 같은 문자열(한글·영문 1자 이상). */
    public static final String LETTER = "[가-힣a-zA-Z]";

    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT);
    private static final Pattern LETTER_PATTERN = Pattern.compile(LETTER);

    private NicknameRules() {
    }

    public static boolean isValidFormat(String value) {
        return value != null && FORMAT_PATTERN.matcher(value).matches();
    }

    public static boolean hasLetter(String value) {
        return value != null && LETTER_PATTERN.matcher(value).find();
    }
}
