package com.team.blog.media.application;

import java.time.Instant;
import java.util.Map;

/**
 * 브라우저가 직접 올릴 주소.
 *
 * @param url       사전 서명 주소
 * @param method    {@code PUT}
 * @param headers   요청에 반드시 넣을 헤더(서명에 포함된 {@code Content-Type})
 * @param expiresAt 만료 시각
 */
public record UploadTarget(String url, String method, Map<String, String> headers, Instant expiresAt) {
}
