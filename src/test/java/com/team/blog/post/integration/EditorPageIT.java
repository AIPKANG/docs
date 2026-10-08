package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;

/** 004 T315: 편집 화면·내 글 최소 목록(research R-11, FR-019, FR-023). */
class EditorPageIT extends IntegrationTestBase {

    @Test
    void editorShowsCurrentContentEscaped() throws Exception {
        long me = writer(members, "editorpage");
        long postId = posts.draft(me, "</script><script>alert(1)</script>", "<b>본문</b>", 3);
        String html = mockMvc.perform(get("/write/{id}", postId).with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("<script>alert(1)</script>").doesNotContain("<b>본문</b>");
        assertThat(html).contains("data-state=").contains("&quot;version&quot;:3").contains("&lt;b&gt;본문&lt;/b&gt;");
        assertThat(html).contains("/js/editor/autosave.js").contains("id=\"save-status\"");
        // 임시글에는 [변경 취소]가 없다
        assertThat(html).doesNotContain("discard-working-copy");
    }

    @Test
    void othersDraftAndTrashedDraftAre404AndGuestIsSentToLogin() throws Exception {
        long me = writer(members, "editorown");
        long other = writer(members, "editoroth");
        long othersPost = posts.draft(other, "남의 글", "", 0);
        long trashed = posts.draft(me, "휴지통", "", 0);
        posts.trash(trashed);
        mockMvc.perform(get("/write/{id}", othersPost).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(get("/write/{id}", trashed).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(get("/write/{id}", 999999).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(get("/write/{id}", othersPost).with(TestAuth.admin(me))).andExpect(status().isNotFound());
        mockMvc.perform(get("/write/{id}", othersPost)).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?redirect=%2Fwrite%2F" + othersPost));
    }

    @Test
    void myPostsListsOwnPostsWithBadges() throws Exception {
        long me = writer(members, "listowner");
        long other = writer(members, "listother");
        posts.draft(me, "", "", 0);
        posts.draft(me, "<i>임시</i>", "", 0);
        long published = posts.published(me, "발행 글", "본문", 2, java.time.Instant.parse("2026-10-01T00:00:00Z"));
        posts.workingCopy(published, "고치는 중", "", 3);
        posts.draft(other, "남의 임시글", "", 0);
        long trashed = posts.draft(me, "휴지통 글", "", 0);
        posts.trash(trashed);

        // 011: 기본 탭은 임시글, 발행 글은 [발행 글] 탭
        String html = mockMvc.perform(get("/manage/posts").with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("(제목 없음)").contains("&lt;i&gt;임시&lt;/i&gt;").contains("임시글 2")
                .doesNotContain("남의 임시글").doesNotContain("휴지통 글");
        String publishedTab = mockMvc.perform(get("/manage/posts").param("tab", "published").with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(publishedTab).contains("발행 글").contains("수정 중");
        assertThat(html).contains("action=\"/write\"").contains("href=\"/manage/posts\"");
        mockMvc.perform(get("/manage/posts")).andExpect(status().isSeeOther());
    }
}
