package com.team.blog.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * REST 오류 본문 {@code { "code": "...", "message": "..." }} (42 §4 이유 코드 형식).
 * 선택 필드 {@code suggestion}(대안 주소), {@code nextAllowedAt}(닉네임 다음 변경 가능 시각)은 값이 있을 때만 나간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String suggestion, Instant nextAllowedAt) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, null);
    }

    public ErrorResponse withSuggestion(String value) {
        return new ErrorResponse(code, message, value, nextAllowedAt);
    }

    public ErrorResponse withNextAllowedAt(Instant value) {
        return new ErrorResponse(code, message, suggestion, value);
    }
}
