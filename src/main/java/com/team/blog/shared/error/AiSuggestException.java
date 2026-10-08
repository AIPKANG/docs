package com.team.blog.shared.error;

/**
 * AI 태그 추천(021, 34 §9) 오류. 코드마다 상태가 정해져 있다: {@code AI_CONSENT_REQUIRED} 409, {@code CONTENT_TOO_SHORT} 422,
 * {@code AI_DAILY_LIMIT} 429, {@code AI_PUBLIC_ONLY} 400, {@code AI_UNAVAILABLE}·{@code AI_BUSY} 503.
 */
public class AiSuggestException extends RuntimeException {

    public static final String CONSENT_REQUIRED = "AI_CONSENT_REQUIRED";
    public static final String CONTENT_TOO_SHORT = "CONTENT_TOO_SHORT";
    public static final String DAILY_LIMIT = "AI_DAILY_LIMIT";
    public static final String PUBLIC_ONLY = "AI_PUBLIC_ONLY";
    public static final String UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String BUSY = "AI_BUSY";

    private final String code;

    public AiSuggestException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public int status() {
        return switch (code) {
            case CONSENT_REQUIRED -> 409;
            case CONTENT_TOO_SHORT -> 422;
            case DAILY_LIMIT -> 429;
            case PUBLIC_ONLY -> 400;
            default -> 503;
        };
    }
}
