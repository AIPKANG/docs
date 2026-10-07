package com.team.blog.account.domain;

import java.util.Objects;

/** 블로그 주소 값 객체 {@code [접두어-]본문}. 생성 시 형식({@link HandleRules#FORMAT})을 검사한다. */
public final class Handle {

    private final HandlePrefix prefix;
    private final String body;

    private Handle(HandlePrefix prefix, String body) {
        this.prefix = Objects.requireNonNull(prefix);
        this.body = Objects.requireNonNull(body);
        if (!HandleRules.isValidFormat(prefix.value() + body)) {
            throw new IllegalArgumentException("invalid handle format");
        }
    }

    public static Handle of(HandlePrefix prefix, String body) {
        return new Handle(prefix, body);
    }

    /** 전체 주소 문자열(접두어 포함)을 해석한다. 형식에 맞지 않으면 {@link IllegalArgumentException}. */
    public static Handle parse(String value) {
        int dash = value.indexOf('-');
        if (dash < 0) {
            return new Handle(HandlePrefix.NONE, value);
        }
        HandlePrefix prefix = HandlePrefix.fromLabel(value.substring(0, dash));
        if (prefix == null) {
            throw new IllegalArgumentException("invalid handle format");
        }
        return new Handle(prefix, value.substring(dash + 1));
    }

    public HandlePrefix prefix() {
        return prefix;
    }

    public String body() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Handle other && prefix == other.prefix && body.equals(other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, body);
    }

    @Override
    public String toString() {
        return prefix.value() + body;
    }
}
