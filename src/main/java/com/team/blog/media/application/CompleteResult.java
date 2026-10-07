package com.team.blog.media.application;

/** 업로드 완료 결과: 공개 주소와 해상도. */
public record CompleteResult(long imageId, String url, int width, int height) {
}
