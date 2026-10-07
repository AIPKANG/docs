package com.team.blog.media.domain;

import java.util.Arrays;
import java.util.Optional;

/** 허용 이미지 형식(DB {@code ck_image_type} 4종). */
public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageFormat> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String normalized = contentType.strip().toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.contentType.equals(normalized)).findFirst();
    }
}
