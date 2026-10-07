package com.team.blog.account.application;

/**
 * 소셜 가입 마무리 요청(contracts/web-routes.md §3).
 *
 * @param handle 주소 본문(접두어는 서버가 가입 수단으로 붙인다)
 * @param email  공급자가 인증한 이메일이 없을 때만 사용자가 입력한 이메일, 아니면 무시
 */
public record SocialSignupCommand(String nickname, String handle, boolean agreeTerms, boolean agreePrivacy,
                                  boolean useSocialPicture, String email) {
}
