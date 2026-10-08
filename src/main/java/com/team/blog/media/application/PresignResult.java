package com.team.blog.media.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** 업로드 승인 결과: 사진 번호와 5분 유효 PUT 주소. 008 글 사진은 썸네일 주소가 함께 온다(없으면 칸 생략). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresignResult(long imageId, String uploadUrl, String method, Map<String, String> headers,
                            Instant expiresAt, String thumbUploadUrl, Map<String, String> thumbHeaders) {

    public PresignResult(long imageId, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {
        this(imageId, uploadUrl, method, headers, expiresAt, null, null);
    }
}
