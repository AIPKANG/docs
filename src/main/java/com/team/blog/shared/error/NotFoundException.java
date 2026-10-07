package com.team.blog.shared.error;

/**
 * 404 단일 이유 코드 {@code NOT_FOUND}. 없음·비공개·탈퇴를 구분하지 않는다(42 §4, 헌법 III) — 이유를 세분화하지 않는다.
 */
public class NotFoundException extends RuntimeException {

    public static final String CODE = "NOT_FOUND";

    public NotFoundException() {
        super(CODE);
    }
}
