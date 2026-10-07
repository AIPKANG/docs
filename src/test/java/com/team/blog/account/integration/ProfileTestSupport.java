package com.team.blog.account.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.team.blog.support.TestAuth;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 003 통합 테스트 공용 요청 모양. */
final class ProfileTestSupport {

    private ProfileTestSupport() {
    }

    static MockHttpServletRequestBuilder patchProfile(long memberId, String json) {
        return patch("/api/me/profile").with(csrf()).with(TestAuth.member(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** JSON 문자열 값(따옴표·역슬래시·줄바꿈 이스케이프). */
    static String q(String value) {
        StringBuilder out = new StringBuilder("\"");
        value.codePoints().forEach(cp -> {
            switch (cp) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (cp < 0x20) {
                        out.append(String.format("\\u%04x", cp));
                    } else {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.append('"').toString();
    }
}
