package com.team.blog.friend.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.application.visibility.VisibilityRule;
import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code GROUP}(030, 강성찬 개인 확장): 글쓴이가 고른 그룹에 든 사람만 읽는다. 공용 목록에는 넣지 않고, 그 사람에게 보이는 블로그 목록에만
 * 더한다({@code PostAccessPolicy#friendBlogCondition}).
 */
@Component
@ConditionalOnProperty(name = "blog.friend.groups.enabled", havingValue = "true", matchIfMissing = true)
public class GroupVisibilityRule implements VisibilityRule {

    public static final String VALUE = "GROUP";

    private final GroupService groups;

    public GroupVisibilityRule(GroupService groups) {
        this.groups = groups;
    }

    @Override
    public String visibility() {
        return VALUE;
    }

    @Override
    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        return viewer.map(v -> groups.canSee(post.id(), v.memberId())).orElse(false);
    }

    @Override
    public String listCondition(String postAlias) {
        return null;
    }
}
