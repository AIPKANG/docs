package com.team.blog.account.application;

/**
 * 이메일 가입 요청(contracts/web-routes.md §1). {@code toString()}에 비밀번호를 넣지 않는다(FR-014).
 */
public record EmailSignupCommand(String email, String handle, String password, String passwordConfirm,
                                 String nickname, boolean agreeTerms, boolean agreePrivacy) {

    @Override
    public String toString() {
        return "EmailSignupCommand[handle=" + handle + ", nickname=" + nickname + "]";
    }
}
