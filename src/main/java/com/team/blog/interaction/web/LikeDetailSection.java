package com.team.blog.interaction.web;

import com.team.blog.interaction.application.LikeService;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.application.ViewCountFormat;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 글 상세의 좋아요 상태(015 FR-018): 내가 눌렀는지(로그인 시 PK 조회 1번)와 표시 숫자. */
@Component
public class LikeDetailSection implements PostDetailSection {

    private final LikeService likeService;

    public LikeDetailSection(LikeService likeService) {
        this.likeService = likeService;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        boolean liked = viewer.map(v -> v.memberId() != post.authorId() && likeService.likedBy(post.id(), v.memberId()))
                .orElse(false);
        model.addAttribute("liked", liked);
        model.addAttribute("likeLabel", ViewCountFormat.format(post.likeCount()));
    }
}
