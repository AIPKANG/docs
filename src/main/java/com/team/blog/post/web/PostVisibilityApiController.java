package com.team.blog.post.web;

import com.team.blog.post.application.PostVisibilityService;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 공개 범위 바꾸기 {@code PATCH /api/posts/{postId}/visibility}(006 contracts). */
@RestController
public class PostVisibilityApiController {

    private final PostVisibilityService visibilityService;
    private final CurrentUserProvider currentUserProvider;

    public PostVisibilityApiController(PostVisibilityService visibilityService, CurrentUserProvider currentUserProvider) {
        this.visibilityService = visibilityService;
        this.currentUserProvider = currentUserProvider;
    }

    @PatchMapping("/api/posts/{postId}/visibility")
    public PostVisibilityService.Result change(@PathVariable long postId, @RequestBody(required = false) JsonNode body) {
        JsonNode value = body == null ? null : body.get("visibility");
        String to = value != null && value.isString() ? value.stringValue() : null;
        return visibilityService.change(currentUserProvider.current(), postId, to);
    }
}
