package com.team.blog.post.web;

import com.team.blog.post.application.MarkdownPreviewService;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 미리보기 API {@code POST /api/markdown/preview}(007 contracts/web-routes.md). */
@RestController
public class MarkdownPreviewApiController {

    private final MarkdownPreviewService previewService;
    private final CurrentUserProvider currentUserProvider;

    public MarkdownPreviewApiController(MarkdownPreviewService previewService, CurrentUserProvider currentUserProvider) {
        this.previewService = previewService;
        this.currentUserProvider = currentUserProvider;
    }

    public record Preview(String html) {
    }

    @PostMapping("/api/markdown/preview")
    public Preview preview(@RequestBody(required = false) JsonNode body) {
        JsonNode content = body == null ? null : body.get("contentMd");
        if (content != null && !content.isString() && !content.isNull()) {
            throw new PostContentException("INVALID_REQUEST");
        }
        String md = content == null || content.isNull() ? null : content.stringValue();
        return new Preview(previewService.preview(currentUserProvider.current(), md));
    }
}
