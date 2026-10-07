package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.shared.error.NotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 블로그 진입 자리표시({@code GET /@{handle}}). 없는·형식 밖·탈퇴 주소는 404 공통 화면.
 * 글 목록·프로필 상단 내용은 009·003이 이 컨트롤러·템플릿을 채운다.
 */
@Controller
public class BlogPageController {

    private final BlogOwnerResolver blogOwnerResolver;

    public BlogPageController(BlogOwnerResolver blogOwnerResolver) {
        this.blogOwnerResolver = blogOwnerResolver;
    }

    @GetMapping("/@{handle}")
    public String blog(@PathVariable("handle") String handle, Model model) {
        BlogOwner owner = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        model.addAttribute("owner", owner);
        model.addAttribute("author", owner.toAuthorDisplay());
        return "blog/home";
    }
}
