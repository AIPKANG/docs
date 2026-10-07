package com.team.blog.account.web;

import com.team.blog.account.application.SocialSignupCommand;

/** 소셜 가입 마무리 폼. {@code handle}은 본문만(접두어는 고정 글자). */
public record SocialSignupForm(String nickname, String handle, Boolean agreeTerms, Boolean agreePrivacy,
                               Boolean useSocialPicture, String email) {

    SocialSignupCommand toCommand() {
        return new SocialSignupCommand(nickname, handle, Boolean.TRUE.equals(agreeTerms),
                Boolean.TRUE.equals(agreePrivacy), Boolean.TRUE.equals(useSocialPicture), email);
    }
}
