package com.team.blog.post.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.support.MemberFixtures;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 004 통합 테스트 공용 요청·회원 도우미. */
final class PostTestSupport {

    private PostTestSupport() {
    }

    static long writer(MemberFixtures members, String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())),
                handle + "@example.com", "Blog#2026ok", true);
    }

    static String body(String title, String content, long baseVersion) {
        return "{\"title\":" + json(title) + ",\"contentMd\":" + json(content) + ",\"baseVersion\":" + baseVersion + "}";
    }

    static MockHttpServletRequestBuilder autosave(long postId, String title, String content, long baseVersion) {
        return put("/api/posts/{id}/autosave", postId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(title, content, baseVersion));
    }

    static MockHttpServletRequestBuilder manualSave(long postId, String title, String content, long baseVersion) {
        return put("/api/posts/{id}/draft", postId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(title, content, baseVersion));
    }

    static MockHttpServletRequestBuilder createApi(String json) {
        return post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
