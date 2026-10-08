package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 009 T909: 개인 블로그 목록(US2, FR-016~FR-020). */
class BlogPageListIT extends IntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void blogShowsOnlyOwnersPublicPostsEvenToOwner() throws Exception {
        long me = writer(members, "blogowner");
        long other = writer(members, "blogother");
        posts.published(me, "내 공개 글", "본문", 1, T0);
        long priv = posts.published(me, "내 비공개 글", "본문", 1, T0.plus(Duration.ofHours(1)));
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        posts.draft(me, "내 임시글", "본문", 0);
        posts.published(other, "남의 글", "본문", 1, T0);

        for (var viewer : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {null, TestAuth.member(me)}) {
            var request = get("/@blogowner");
            if (viewer != null) {
                request = request.with(viewer);
            }
            String html = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("내 공개 글").contains("공개 글 1").doesNotContain("내 비공개 글")
                    .doesNotContain("내 임시글").doesNotContain("남의 글").doesNotContain("class=\"card-author\"");
        }
        mockMvc.perform(get("/api/members/blogowner/posts"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("내 공개 글"));
    }

    @Test
    void pagingAndEmptyStatesAndMissingBlog() throws Exception {
        long me = writer(members, "blogpager");
        assertThat(mockMvc.perform(get("/@blogpager")).andReturn().getResponse().getContentAsString())
                .contains("아직 공개한 글이 없어요");
        assertThat(mockMvc.perform(get("/@blogpager").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("첫 글을 써 보세요");
        for (int i = 0; i < 11; i++) {
            posts.published(me, "블로그 글 " + i, "본문", 1, T0.plus(Duration.ofHours(i)));
        }
        String html = mockMvc.perform(get("/@blogpager")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("공개 글 11").contains("블로그 글 10").contains("더 보기").doesNotContain("블로그 글 1<");
        mockMvc.perform(get("/@nobodyhere")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/members/nobodyhere/posts")).andExpect(status().isNotFound());
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", me);
        mockMvc.perform(get("/@blogpager")).andExpect(status().isNotFound());
    }
}
