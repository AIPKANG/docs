package com.team.blog.media.application;

/** 업로드 승인 요청(contracts/web-routes.md §3). 올리는 사람은 로그인 정보로만 정한다. */
public record PresignCommand(String purpose, String contentType, Long size, String originalName) {
}
