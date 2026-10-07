package com.team.blog.shared.error;

import java.util.List;

/**
 * 프로필·계정 설정 검사 실패 → {@code VALIDATION_FAILED} + {@code errors[]}(003 research R-4).
 * 실패한 칸을 모두 담는다. HTTP는 400, 모두 충돌 종류면 409.
 */
public class ProfileValidationException extends RuntimeException {

    public static final String CODE = "VALIDATION_FAILED";

    private final List<FieldError> errors;

    public ProfileValidationException(List<FieldError> errors) {
        super(CODE);
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be empty");
        }
        this.errors = List.copyOf(errors);
    }

    public ProfileValidationException(FieldError error) {
        this(List.of(error));
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public boolean isConflictOnly() {
        return errors.stream().allMatch(FieldError::conflict);
    }
}
