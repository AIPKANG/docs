package com.team.blog.discovery.web;

import com.team.blog.discovery.application.ViewProperties;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 글 상세의 조회수 안내 문구(016 FR-016). */
@Component
public class ViewDetailSection implements PostDetailSection {

    private final ViewProperties properties;

    public ViewDetailSection(ViewProperties properties) {
        this.properties = properties;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        model.addAttribute("viewNotice", properties.notice());
    }
}
