package com.team.blog.interaction.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 014 테스트 요청 도우미. */
final class CommentTestSupport {

    private CommentTestSupport() {
    }

    static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static MockHttpServletRequestBuilder create(long postId, String content, Long replyTo) {
        return post("/api/posts/{id}/comments", postId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + json(content) + (replyTo == null ? "" : ",\"replyToCommentId\":" + replyTo) + "}");
    }

    static MockHttpServletRequestBuilder edit(long commentId, String content) {
        return patch("/api/comments/{id}", commentId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + json(content) + "}");
    }

    static MockHttpServletRequestBuilder remove(long commentId) {
        return delete("/api/comments/{id}", commentId).with(csrf());
    }
}
