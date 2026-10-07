package com.team.blog.media.application;

/**
 * 저장소에 올라간 파일의 확인 결과.
 *
 * @param size        실제 크기(바이트)
 * @param contentType 저장소가 기록한 Content-Type
 * @param head        앞부분 바이트(최대 {@link #HEAD_BYTES})
 */
public record StoredObject(long size, String contentType, byte[] head) {

    public static final int HEAD_BYTES = 64 * 1024;
}
