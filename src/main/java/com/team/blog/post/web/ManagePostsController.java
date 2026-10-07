package com.team.blog.post.web;

import com.team.blog.post.application.PostManageQuery;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 내 글 최소 목록({@code GET /manage/posts}, research R-11). 011이 탭·페이지·휴지통을 더한다. */
@Controller
public class ManagePostsController {

    private final PostManageQuery manageQuery;
    private final CurrentUserProvider currentUserProvider;

    public ManagePostsController(PostManageQuery manageQuery, CurrentUserProvider currentUserProvider) {
        this.manageQuery = manageQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/manage/posts")
    public String myPosts(Model model) {
        model.addAttribute("posts", manageQuery.myPosts(currentUserProvider.current()));
        return "post/manage";
    }
}
