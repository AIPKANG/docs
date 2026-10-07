package com.team.blog.post.markdown;

import java.util.List;

/**
 * 렌더링 결과.
 *
 * @param html          정화된 본문 HTML
 * @param excerpt       목록 카드 요약(글자, 최대 200자, 10 §2-1)
 * @param imageUrls     본문에 나온 우리 저장소 이미지 주소(나온 순서, 중복 제거) — 005 썸네일·008 사진 연결
 * @param renderVersion 이 결과를 만든 규칙 버전
 */
public record RenderedContent(String html, String excerpt, List<String> imageUrls, int renderVersion) {
}
