package com.team.blog.shared.error;

/** 아직 받지 않는 사진 용도(글 사진 {@code POST}는 008에서 받는다) → 400 {@code IMAGE_PURPOSE_NOT_SUPPORTED}. */
public class ImagePurposeNotSupportedException extends RuntimeException {

    public static final String CODE = "IMAGE_PURPOSE_NOT_SUPPORTED";

    public ImagePurposeNotSupportedException() {
        super(CODE);
    }
}
