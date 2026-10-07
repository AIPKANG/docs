package com.team.blog.media.application;

import java.time.Instant;
import java.util.Map;

/** 업로드 승인 결과: 사진 번호와 5분 유효 PUT 주소. */
public record PresignResult(long imageId, String uploadUrl, String method, Map<String, String> headers,
                            Instant expiresAt) {
}
