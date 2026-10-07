package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 005 T510: 글 상세 접근(42 §5-1, FR-002, FR-023). */
class PostDetailIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void readersSeePublicOnlyAuthorSeesOwnDraftAndPrivate() throws Exception {
        long me = writer(members, "detailown");
        long other = writer(members, "detailoth");
        long pub = posts.published(me, "공개 글", "본문", 1, T);
        long draft = posts.draft(me, "임시 글", "본문", 0);
        long priv = posts.published(me, "비공개 글", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long trashed = posts.published(me, "휴지통 글", "본문", 1, T);
        posts.trash(trashed);

        mockMvc.perform(get("/@detailown/posts/{id}", pub)).andExpect(status().isOk());
        mockMvc.perform(get("/@detailown/posts/{id}", pub).with(TestAuth.member(other))).andExpect(status().isOk());
        String notFound = null;
        for (long hidden : new long[] {draft, priv, trashed, 999999}) {
            for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                    get("/@detailown/posts/{id}", hidden), get("/@detailown/posts/{id}", hidden).with(TestAuth.member(other)),
                    get("/@detailown/posts/{id}", hidden).with(TestAuth.admin(other))}) {
                String body = mockMvc.perform(request).andExpect(status().isNotFound())
                        .andReturn().getResponse().getContentAsString();
                if (notFound == null) {
                    notFound = body;
                }
            }
        }
        assertThat(mockMvc.perform(get("/@detailown/posts/{id}", draft).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains("임시저장");
        assertThat(mockMvc.perform(get("/@detailown/posts/{id}", priv).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains("나만 보기");
        mockMvc.perform(get("/@detailown/posts/{id}", trashed).with(TestAuth.member(me))).andExpect(status().isNotFound());
        // 다른 블로그 주소로는 열리지 않는다
        mockMvc.perform(get("/@detailoth/posts/{id}", pub)).andExpect(status().isNotFound());
    }

    @Test
    void editButtonOnlyForAuthorAndEditingNoticeOnlyForAuthor() throws Exception {
        long me = writer(members, "editbtn");
        long other = writer(members, "editbtnx");
        long pub = posts.published(me, "글", "본문", 1, T);
        posts.workingCopy(pub, "고치는 중", "", 2);
        String mine = mockMvc.perform(get("/@editbtn/posts/{id}", pub).with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("href=\"/write/" + pub + "\"").contains("고치는 중인 내용이 있어요");
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/@editbtn/posts/{id}", pub), get("/@editbtn/posts/{id}", pub).with(TestAuth.member(other))}) {
            String html = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
            assertThat(html).doesNotContain("/write/" + pub).doesNotContain("고치는 중").contains("<p>발행본</p>");
        }
    }
}
