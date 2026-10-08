package com.team.blog.shared.web;

/**
 * 링크 미리보기·검색 엔진 메타(010 R-4). 레이아웃이 모델의 {@code pageMeta}로 그린다(값은 Thymeleaf 속성 이스케이프).
 *
 * @param modifiedTime 없으면 null
 */
public record PageMeta(String title, String description, String canonicalUrl, String imageUrl, String publishedTime,
                       String modifiedTime) {
}
