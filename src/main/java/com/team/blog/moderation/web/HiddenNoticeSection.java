package com.team.blog.moderation.web;

import com.team.blog.moderation.application.ReportReason;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 숨긴 글을 작성자가 볼 때 사유 이름(022 FR-021). 조회는 없다. */
@Component
public class HiddenNoticeSection implements PostDetailSection {

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        if (post.hidden()) {
            model.addAttribute("hiddenReasonLabel", ReportReason.labelOf(post.hiddenReason()));
        }
    }
}
