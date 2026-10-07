package com.team.blog.account.application;

/** {@code /@{handle}} 주인 요약(활성 회원만). 블로그 상단(009)·프로필(003)에 넘긴다. */
public record BlogOwner(long memberId, String handle, String nickname, String bio, String profileImageUrl) {

    /** 블로그 상단 프로필 아이콘(003). */
    public ProfileAvatar avatar() {
        return new ProfileAvatar(handle, nickname, profileImageUrl);
    }

    public AuthorDisplay toAuthorDisplay() {
        return AuthorDisplay.of(handle, nickname, null);
    }
}
