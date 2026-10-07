package com.team.blog.shared.error;

import java.time.Instant;

/** 닉네임을 바꾼 뒤 제한 기간(30일) 안에 다시 바꾸려 함 → 409 {@code NICKNAME_CHANGE_TOO_SOON} + {@code nextAllowedAt}. */
public class NicknameChangeTooSoonException extends RuntimeException {

    public static final String CODE = "NICKNAME_CHANGE_TOO_SOON";

    private final Instant nextAllowedAt;

    public NicknameChangeTooSoonException(Instant nextAllowedAt) {
        super(CODE);
        this.nextAllowedAt = nextAllowedAt;
    }

    public Instant getNextAllowedAt() {
        return nextAllowedAt;
    }
}
