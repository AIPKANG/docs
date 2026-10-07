package com.team.blog.account.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** 닉네임 값 객체. 값은 NFC로 정리된 문자열이고 형식·글자 포함 규칙을 만족한다. 비교는 {@link #lowerKey()}로 한다. */
public final class Nickname {

    private final String value;

    private Nickname(String value) {
        this.value = value;
    }

    /** 이미 정리(trim+NFC)·검사된 값으로 만든다. 형식에 맞지 않으면 {@link IllegalArgumentException}. */
    public static Nickname of(String value) {
        Objects.requireNonNull(value);
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || !NicknameRules.isValidFormat(value) || !NicknameRules.hasLetter(value)) {
            throw new IllegalArgumentException("invalid nickname format");
        }
        return new Nickname(value);
    }

    public String value() {
        return value;
    }

    /** 대소문자 무시 비교용 키({@code uq_member_nickname}의 {@code lower(nickname)}과 같은 기준). */
    public String lowerKey() {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Nickname other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
