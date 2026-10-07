package com.team.blog.shared.security;

/**
 * 현재 로그인한 회원. SecurityContext에서만 만든다 — 요청 값(본문·쿼리)으로 회원 ID를 받지 않는다(헌법 III).
 *
 * @param memberId {@code member.id}
 * @param role     {@code member.role} 값({@code USER}/{@code ADMIN})
 */
public record CurrentUser(long memberId, String role) {
}
