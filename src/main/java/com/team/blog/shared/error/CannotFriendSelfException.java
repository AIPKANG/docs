package com.team.blog.shared.error;

/** 자기 자신에게 친구 요청 → 400 {@code CANNOT_FRIEND_SELF}(025). */
public class CannotFriendSelfException extends RuntimeException {

    public static final String CODE = "CANNOT_FRIEND_SELF";

    public CannotFriendSelfException() {
        super(CODE);
    }
}
