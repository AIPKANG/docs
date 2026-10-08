package com.team.blog.friend.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.application.visibility.VisibilityRule;
import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@code FRIENDS}(025, 06 §6-3): 글쓴이와 수락된 친구만 읽는다. 어떤 공용 목록(홈·태그·검색·트렌딩·피드·sitemap)에도 넣지 않는다 —
 * 친구가 보는 개인 블로그 목록만 따로 더한다({@code PostAccessPolicy#friendBlogCondition}).
 */
@Component
public class FriendsVisibilityRule implements VisibilityRule {

    public static final String VALUE = "FRIENDS";

    private final FriendQuery friends;

    public FriendsVisibilityRule(FriendQuery friends) {
        this.friends = friends;
    }

    @Override
    public String visibility() {
        return VALUE;
    }

    @Override
    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        return viewer.map(v -> friends.areFriends(post.authorId(), v.memberId())).orElse(false);
    }

    @Override
    public String listCondition(String postAlias) {
        return null;
    }
}
