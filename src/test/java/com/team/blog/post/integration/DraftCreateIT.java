package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.createApi;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 004 T311: [새 글]은 편집 버전 0인 임시글을 바로 만든다(FR-015, US1-1, 42 §5-2). */
class DraftCreateIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private long onlyPostId() {
        return jdbc.queryForObject("SELECT id FROM post", Long.class);
    }

    @Test
    void newPostButtonCreatesDraftAndRedirectsToEditor() throws Exception {
        long id = writer(members, "writerone");
        mockMvc.perform(post("/write").with(csrf()).with(TestAuth.member(id)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/write/" + onlyPostId()));
        Map<String, Object> row = posts.post(onlyPostId());
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("edit_version")).isEqualTo(0L);
        assertThat(row.get("author_id")).isEqualTo(id);
        assertThat(row.get("title")).isEqualTo("");
        assertThat(row.get("visibility")).isEqualTo("PUBLIC");
    }

    @Test
    void newPostStartsWithMemberDefaultVisibility() throws Exception {
        long id = writer(members, "writertwo");
        jdbc.update("UPDATE member SET default_visibility = 'PRIVATE' WHERE id = ?", id);
        mockMvc.perform(post("/write").with(csrf()).with(TestAuth.member(id))).andExpect(status().isSeeOther());
        assertThat(posts.post(onlyPostId()).get("visibility")).isEqualTo("PRIVATE");
    }

    @Test
    void apiCreatesDraftWithContent() throws Exception {
        long id = writer(members, "writerthree");
        mockMvc.perform(createApi("{\"title\":\"따로 저장\",\"contentMd\":\"본문\",\"tags\":[\"x\"]}").with(TestAuth.member(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(onlyPostId()))
                .andExpect(jsonPath("$.version").value(0));
        assertThat(posts.post(onlyPostId())).containsEntry("title", "따로 저장").containsEntry("content_md", "본문");
    }

    @Test
    void guestAndUnverifiedCannotCreate() throws Exception {
        mockMvc.perform(createApi("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/write").with(csrf())).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?redirect=%2Fwrite"));
        long unverified = members.localMember("unverified", "미인증", "unverified@example.com", "Blog#2026ok", false);
        mockMvc.perform(createApi("{}").with(TestAuth.member(unverified)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(posts.count()).isZero();
    }
}
