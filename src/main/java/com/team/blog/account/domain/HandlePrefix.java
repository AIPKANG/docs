package com.team.blog.account.domain;

/** 블로그 주소 접두어. 가입 수단과 1:1 대응한다(08 §2). 새 소셜 수단은 값 추가 + DB CHECK 변경이 함께 필요하다. */
public enum HandlePrefix {
    NONE(""),
    GO("go-"),
    GI("gi-");

    private final String value;

    HandlePrefix(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static HandlePrefix of(Provider provider) {
        return switch (provider) {
            case LOCAL -> NONE;
            case GOOGLE -> GO;
            case GITHUB -> GI;
        };
    }

    /** 첫 {@code -} 앞부분({@code go}/{@code gi})에 해당하는 접두어. 알려진 접두어가 아니면 {@code null}. */
    public static HandlePrefix fromLabel(String label) {
        return switch (label) {
            case "go" -> GO;
            case "gi" -> GI;
            default -> null;
        };
    }
}
