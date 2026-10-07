package com.team.blog.shared.error;

/** 비밀번호 변경 시 현재 비밀번호가 틀림 → 400 {@code CURRENT_PASSWORD_MISMATCH}(연속 실패 수 +1). */
public class CurrentPasswordMismatchException extends RuntimeException {

    public static final String CODE = "CURRENT_PASSWORD_MISMATCH";

    public CurrentPasswordMismatchException() {
        super(CODE);
    }
}
