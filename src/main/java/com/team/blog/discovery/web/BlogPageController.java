package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 개인 블로그({@code GET /@{handle}}, 009 FR-016~FR-020): 프로필 머리말 + 공개 글 수 + 그 회원의 공개 글 카드.
 * 작성자 본인이 봐도 비공개 글은 나오지 않는다. 없는·형식 밖·탈퇴 주소는 404, 대문자 주소는 필터가 301.
 */
@Controller
public class BlogPageController {

    private final BlogOwnerResolver blogOwnerResolver;
    private final PostListQuery listQuery;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public BlogPageController(BlogOwnerResolver blogOwnerResolver, PostListQuery listQuery,
                              CurrentUserProvider currentUserProvider, Clock clock) {
        this.blogOwnerResolver = blogOwnerResolver;
        this.listQuery = listQuery;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/@{handle}")
    public String blog(@PathVariable("handle") String handle,
                       @RequestParam(value = "cursor", required = false) String cursor, Model model) {
        BlogOwner owner = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        CardPage page;
        try {
            page = listQuery.blog(owner.memberId(), cursor);
        } catch (PostContentException e) {
            page = listQuery.blog(owner.memberId(), null);
            cursor = null;
        }
        model.addAttribute("owner", owner);
        model.addAttribute("author", owner.toAuthorDisplay());
        model.addAttribute("publicCount", listQuery.publicCount(owner.memberId()));
        model.addAttribute("isOwner", currentUserProvider.current().map(CurrentUser::memberId)
                .map(id -> id == owner.memberId()).orElse(false));
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", "/api/members/" + owner.handle() + "/posts");
        CardModel.fill(model, page, clock.instant());
        return "blog/home";
    }
}
