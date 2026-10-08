package com.team.blog.shared.error;

/** 관리자가 숨긴 댓글은 작성자도 수정할 수 없음 → 409 {@code COMMENT_HIDDEN}(21 §7). */
public class CommentHiddenException extends RuntimeException {

    public static final String CODE = "COMMENT_HIDDEN";

    public CommentHiddenException() {
        super(CODE);
    }
}
