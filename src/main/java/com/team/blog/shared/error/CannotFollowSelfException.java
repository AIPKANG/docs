package com.team.blog.shared.error;

/** 자기 자신 팔로우 → 400 {@code CANNOT_FOLLOW_SELF}(018 FR-004). */
public class CannotFollowSelfException extends RuntimeException {

    public static final String CODE = "CANNOT_FOLLOW_SELF";

    public CannotFollowSelfException() {
        super(CODE);
    }
}
