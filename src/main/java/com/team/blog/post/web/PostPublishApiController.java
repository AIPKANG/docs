package com.team.blog.post.web;

import com.team.blog.post.application.PostPublishService;
import com.team.blog.post.application.PublishCommand;
import com.team.blog.post.application.PublishResult;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 발행 API {@code POST /api/posts/{postId}/publish} + {@code Idempotency-Key}(05 §5). 작성자 값 같은 다른 칸은 무시한다. */
@RestController
public class PostPublishApiController {

    private final PostPublishService publishService;
    private final CurrentUserProvider currentUserProvider;

    public PostPublishApiController(PostPublishService publishService, CurrentUserProvider currentUserProvider) {
        this.publishService = publishService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/posts/{postId}/publish")
    public PublishResult publish(@PathVariable long postId,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                 @RequestBody(required = false) JsonNode body) {
        return publishService.publish(currentUserProvider.current(), postId, idempotencyKey, command(body));
    }

    static PublishCommand command(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode base = body.get("baseVersion");
        Long baseVersion = base != null && base.isIntegralNumber() && base.canConvertToLong() ? base.longValue() : null;
        List<String> tags = null;
        JsonNode tagsNode = body.get("tags");
        if (tagsNode != null && !tagsNode.isNull()) {
            if (!tagsNode.isArray()) {
                throw new PostContentException("INVALID_REQUEST");
            }
            tags = new ArrayList<>();
            for (JsonNode tag : tagsNode) {
                tags.add(tag.isString() ? tag.stringValue() : tag.toString());
            }
        }
        return new PublishCommand(text(body, "title"), text(body, "contentMd"), tags, text(body, "visibility"), baseVersion);
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new PostContentException("INVALID_REQUEST");
        }
        return node.stringValue();
    }
}
