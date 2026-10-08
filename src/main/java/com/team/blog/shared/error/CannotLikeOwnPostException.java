package com.team.blog.shared.error;

/** 자기 글 좋아요 → 400 {@code CANNOT_LIKE_OWN_POST}(42 P-9, specs/README 결정 2026-10-07). */
public class CannotLikeOwnPostException extends RuntimeException {

    public static final String CODE = "CANNOT_LIKE_OWN_POST";

    public CannotLikeOwnPostException() {
        super(CODE);
    }
}
