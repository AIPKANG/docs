package com.team.blog.interaction.web;

import com.team.blog.interaction.application.CommentService;
import com.team.blog.interaction.application.CommentView;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** 스크립트 없는 환경의 댓글 작성 폼(014 FR-019). 처리 후 그 댓글 위치로 303. */
@Controller
public class CommentFormController {

    private final CommentService commentService;
    private final CurrentUserProvider currentUserProvider;

    public CommentFormController(CommentService commentService, CurrentUserProvider currentUserProvider) {
        this.commentService = commentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/@{handle}/posts/{postId}/comments")
    public RedirectView create(@PathVariable("handle") String handle, @PathVariable("postId") long postId,
                               @RequestParam(value = "content", required = false) String content,
                               @RequestParam(value = "replyToCommentId", required = false) Long replyTo) {
        CommentView created = commentService.create(currentUserProvider.current(), postId, content, replyTo);
        RedirectView view = new RedirectView("/@" + handle + "/posts/" + postId + "?comment=" + created.id() + "#comment-" + created.id());
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return view;
    }
}
