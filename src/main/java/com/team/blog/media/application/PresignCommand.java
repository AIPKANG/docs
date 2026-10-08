package com.team.blog.media.application;

/**
 * 업로드 승인 요청(contracts/web-routes.md §3). 올리는 사람은 로그인 정보로만 정한다.
 *
 * @param thumbSize 008 글 사진: 썸네일 크기(없으면 썸네일 없이)
 */
public record PresignCommand(String purpose, String contentType, Long size, String originalName, Long thumbSize) {

    public PresignCommand(String purpose, String contentType, Long size, String originalName) {
        this(purpose, contentType, size, originalName, null);
    }
}
