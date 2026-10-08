package com.team.blog.shared.error;

/** 관리자 권한이 있는 회원의 탈퇴 → 409 {@code ADMIN_CANNOT_WITHDRAW}(023 FR-007). */
public class AdminCannotWithdrawException extends RuntimeException {

    public static final String CODE = "ADMIN_CANNOT_WITHDRAW";

    public AdminCannotWithdrawException() {
        super(CODE);
    }
}
