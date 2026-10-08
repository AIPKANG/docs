package com.team.blog.interaction.web;

import com.team.blog.interaction.application.CommentPage;
import com.team.blog.interaction.application.CommentQuery;
import com.team.blog.interaction.application.CommentService;
import com.team.blog.interaction.application.CommentView;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 댓글 API(014 contracts, 21 §5~§8). 작성자는 로그인 정보로만 정한다. */
@RestController
public class CommentApiController {

    private final CommentService commentService;
    private final CommentQuery commentQuery;
    private final CurrentUserProvider currentUserProvider;

    public CommentApiController(CommentService commentService, CommentQuery commentQuery,
                                CurrentUserProvider currentUserProvider) {
        this.commentService = commentService;
        this.commentQuery = commentQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentView> create(@PathVariable long postId, @RequestBody(required = false) JsonNode body) {
        String content = text(body, "content");
        JsonNode reply = body == null ? null : body.get("replyToCommentId");
        Long replyTo = reply != null && reply.isIntegralNumber() ? reply.longValue() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(currentUserProvider.current(), postId, content, replyTo));
    }

    @GetMapping("/api/posts/{postId}/comments")
    public CommentPage list(@PathVariable long postId, @RequestParam(value = "cursor", required = false) String cursor,
                            @RequestParam(value = "before", required = false) String before,
                            @RequestParam(value = "around", required = false) Long around, HttpServletResponse response) {
        CommentPage page = commentQuery.page(currentUserProvider.current(), postId, cursor, before, around);
        response.setHeader("Cache-Control", "private, no-store");
        return page;
    }

    @GetMapping("/api/comments/{rootId}/replies")
    public CommentPage replies(@PathVariable long rootId, @RequestParam(value = "cursor", required = false) String cursor,
                               HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return commentQuery.replies(currentUserProvider.current(), rootId, cursor);
    }

    @PatchMapping("/api/comments/{commentId}")
    public CommentView update(@PathVariable long commentId, @RequestBody(required = false) JsonNode body) {
        return commentService.update(currentUserProvider.current(), commentId, text(body, "content"));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable long commentId) {
        commentService.delete(currentUserProvider.current(), commentId);
        return ResponseEntity.noContent().build();
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body == null ? null : body.get(field);
        if (node != null && !node.isNull() && !node.isString()) {
            throw new PostContentException("INVALID_REQUEST");
        }
        return node == null || node.isNull() ? null : node.stringValue();
    }
}
