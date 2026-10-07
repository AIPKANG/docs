package com.team.blog.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * REST 오류 본문 {@code { "code": "...", "message": "..." }} (42 §4 이유 코드 형식).
 * 선택 필드 {@code suggestion}(대안 주소), {@code nextAllowedAt}(닉네임 다음 변경 가능 시각), {@code errors}(003 칸별 오류 목록),
 * {@code detail}(003 사진 거부 이유)은 값이 있을 때만 나간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String suggestion, Instant nextAllowedAt, List<Item> errors,
                            String detail) {

    /** 칸별 오류 항목(11 §5). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String field, String code, String message, Instant nextAllowedAt) {
    }

    public ErrorResponse(String code, String message, String suggestion, Instant nextAllowedAt) {
        this(code, message, suggestion, nextAllowedAt, null, null);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, null, null, null);
    }

    public ErrorResponse withSuggestion(String value) {
        return new ErrorResponse(code, message, value, nextAllowedAt, errors, detail);
    }

    public ErrorResponse withNextAllowedAt(Instant value) {
        return new ErrorResponse(code, message, suggestion, value, errors, detail);
    }

    public ErrorResponse withErrors(List<Item> value) {
        return new ErrorResponse(code, message, suggestion, nextAllowedAt, value, detail);
    }

    public ErrorResponse withDetail(String value) {
        return new ErrorResponse(code, message, suggestion, nextAllowedAt, errors, value);
    }
}
