package com.team.blog.shared.error;

/**
 * 사진 규격 위반 → 400 {@code IMAGE_INVALID} + {@code detail}(003 data-model §3.1).
 * presign: {@code TYPE}·{@code SIZE}. complete: {@code MISSING}·{@code SIZE}·{@code CONTENT_MISMATCH}·{@code DIMENSION}·
 * {@code METADATA}(이때 저장소 파일·행은 이미 지웠다).
 */
public class ImageInvalidException extends RuntimeException {

    public static final String CODE = "IMAGE_INVALID";

    public enum Detail { TYPE, SIZE, MISSING, CONTENT_MISMATCH, DIMENSION, METADATA, THUMBNAIL, FRAMES }

    private final Detail detail;

    public ImageInvalidException(Detail detail) {
        super(CODE + ":" + detail);
        this.detail = detail;
    }

    public Detail getDetail() {
        return detail;
    }
}
