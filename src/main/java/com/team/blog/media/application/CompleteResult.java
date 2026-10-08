package com.team.blog.media.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 업로드 완료 결과: 공개 주소와 해상도. 008 글 사진은 썸네일 주소(없으면 칸 생략). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompleteResult(long imageId, String url, int width, int height, String thumbUrl) {

    public CompleteResult(long imageId, String url, int width, int height) {
        this(imageId, url, width, height, null);
    }
}
