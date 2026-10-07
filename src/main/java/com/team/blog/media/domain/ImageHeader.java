package com.team.blog.media.domain;

/**
 * 앞부분 바이트로 읽은 이미지 정보.
 *
 * @param hasMetadata EXIF·XMP 같은 메타데이터 조각이 있는지(촬영 위치가 남았을 수 있음)
 */
public record ImageHeader(ImageFormat format, int width, int height, boolean hasMetadata) {
}
