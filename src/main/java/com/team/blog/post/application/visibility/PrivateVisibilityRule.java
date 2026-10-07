package com.team.blog.post.application.visibility;

import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@code PRIVATE}: 작성자만(관리자 포함 다른 사람은 못 봄), 어떤 목록에도 나오지 않는다. */
@Component
public class PrivateVisibilityRule implements VisibilityRule {

    @Override
    public String visibility() {
        return "PRIVATE";
    }

    @Override
    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        return false;
    }

    @Override
    public String listCondition(String postAlias) {
        return null;
    }
}
