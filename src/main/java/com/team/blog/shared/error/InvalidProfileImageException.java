package com.team.blog.shared.error;

/**
 * 프로필에 연결할 수 없는 사진(남의 것·글용·업로드 미완료·삭제됨·없음 — 구분하지 않음, 11 §5) → 400 {@code INVALID_PROFILE_IMAGE}.
 * 프로필 저장에서는 {@code VALIDATION_FAILED}의 {@code profileImageId} 항목으로 바뀐다.
 */
public class InvalidProfileImageException extends RuntimeException {

    public static final String CODE = "INVALID_PROFILE_IMAGE";

    public InvalidProfileImageException() {
        super(CODE);
    }
}
