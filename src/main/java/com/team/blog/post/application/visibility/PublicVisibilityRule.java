package com.team.blog.post.application.visibility;

import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@code PUBLIC}: 누구나(비회원 포함), 공용 목록에 나온다. */
@Component
public class PublicVisibilityRule implements VisibilityRule {

    @Override
    public String visibility() {
        return "PUBLIC";
    }

    @Override
    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        return true;
    }

    @Override
    public String listCondition(String postAlias) {
        return postAlias + ".visibility = 'PUBLIC'";
    }
}
