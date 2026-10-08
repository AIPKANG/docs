package com.team.blog.friend.web;

import com.team.blog.friend.application.GroupService;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 그룹 공개 글(030): 글쓴이에게 보여 줄 그룹 고르기를 보여 준다. */
@Component
public class GroupDetailSection implements PostDetailSection {

    private final GroupService groups;

    public GroupDetailSection(GroupService groups) {
        this.groups = groups;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        boolean author = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (author && "GROUP".equals(post.visibility())) {
            model.addAttribute("myGroups", groups.groups(post.authorId()));
            model.addAttribute("postGroupIds", groups.postGroups(post.id()));
        }
    }
}
