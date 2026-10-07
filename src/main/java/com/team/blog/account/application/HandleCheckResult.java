package com.team.blog.account.application;

/**
 * 블로그 주소 사용 가능 여부 ({@code GET /api/handles/availability} 응답).
 *
 * @param available  사용 가능 여부
 * @param reason     {@code INVALID_FORMAT}·{@code RESERVED}·{@code BANNED_WORD}·{@code DUPLICATE} 또는 null
 * @param suggestion 대안 주소({@code RESERVED}·{@code DUPLICATE}일 때만) 또는 null
 */
public record HandleCheckResult(boolean available, String reason, String suggestion) {

    public static HandleCheckResult ok() {
        return new HandleCheckResult(true, null, null);
    }
}
