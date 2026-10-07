package com.team.blog.post.web;

import com.team.blog.post.application.PostDetailQuery;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.KoreanDateFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 글 상세 최소 화면({@code GET /@{handle}/posts/{id}}, 005 R-8). 010이 조회수·댓글·좋아요·OG를 더한다. */
@Controller
public class PostDetailController {

    private final PostDetailQuery detailQuery;
    private final CurrentUserProvider currentUserProvider;

    public PostDetailController(PostDetailQuery detailQuery, CurrentUserProvider currentUserProvider) {
        this.detailQuery = detailQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/@{handle}/posts/{postId}")
    public String detail(@PathVariable("handle") String handle, @PathVariable("postId") long postId, Model model) {
        PostDetailQuery.Detail detail = detailQuery.find(currentUserProvider.current(), handle, postId);
        model.addAttribute("owner", detail.owner());
        model.addAttribute("author", detail.owner().toAuthorDisplay());
        model.addAttribute("post", detail.post());
        model.addAttribute("viewerIsAuthor", detail.viewerIsAuthor());
        model.addAttribute("publishedDate", detail.post().publishedAt() == null ? null
                : KoreanDateFormatter.yearMonthDay(detail.post().publishedAt()));
        model.addAttribute("editedDate", detail.post().editedAt() == null ? null
                : KoreanDateFormatter.monthDay(detail.post().editedAt()));
        return "post/detail";
    }
}
