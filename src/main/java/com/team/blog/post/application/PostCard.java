package com.team.blog.post.application;

import com.team.blog.account.application.ProfileAvatar;
import java.time.Instant;

/** 글 카드(10 §2). 본문 칸은 읽지 않는다. */
public record PostCard(long id, String url, String title, String excerpt, String thumbnailUrl, Instant firstPublicAt,
                       int commentCount, int likeCount, Author author) {

    public record Author(String handle, String nickname, String profileImageUrl) {

        public ProfileAvatar avatar() {
            return new ProfileAvatar(handle, nickname, profileImageUrl);
        }
    }
}
