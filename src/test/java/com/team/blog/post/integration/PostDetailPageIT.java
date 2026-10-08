package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 010 T1006: 글 상세(주소 처리·화면 구성·메타·캐시·조회 수). */
class PostDetailPageIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @MockitoSpyBean
    JdbcTemplate jdbc;

    private long post(long author, String title) {
        long id = posts.published(author, title, "본문", 1, T);
        jdbc.update("""
                UPDATE post SET content_html = ?, excerpt = ?, view_count = 12345, like_count = 7, comment_count = 3 WHERE id = ?
                """, "<h2 id=\"h-a\">a</h2><p><img src=\"http://localhost:9000/blog-images/images/2026/10/a.webp\" alt=\"\" /></p>",
                "요약 " + "가".repeat(195), id);
        return id;
    }

    @Test
    void addressHandlingOrder() throws Exception {
        long me = writer(members, "addrowner");
        long other = writer(members, "addrother");
        long id = post(me, "주소 처리");
        mockMvc.perform(get("/@AddrOwner/posts/{id}", id)).andExpect(status().isMovedPermanently());
        mockMvc.perform(get("/@addrowner/posts/abc")).andExpect(status().isNotFound());
        mockMvc.perform(get("/@addrowner/posts/0")).andExpect(status().isNotFound());
        mockMvc.perform(get("/@addrowner/posts/99999999999999999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/@addrother/posts/{id}", id)).andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/@addrowner/posts/" + id));
        long draft = posts.draft(me, "임시", "", 0);
        mockMvc.perform(get("/@addrowner/posts/{id}", draft).with(TestAuth.member(me)))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/write/" + draft));
        mockMvc.perform(get("/@addrowner/posts/{id}", draft).with(TestAuth.member(other))).andExpect(status().isNotFound());
        // 볼 수 없는 글은 다른 블로그 주소로 열어도 301이 아니라 404(존재를 드러내지 않음)
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", id);
        mockMvc.perform(get("/@addrother/posts/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void publicPostPageMetaCacheAndLayout() throws Exception {
        long me = writer(members, "detailmeta");
        long id = post(me, "메타 \"제목\" <b>");
        String html = mockMvc.perform(get("/@detailmeta/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("<title>메타 &quot;제목&quot; &lt;b&gt; - detailmeta</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost/@detailmeta/posts/" + id + "\">")
                .contains("<meta property=\"og:type\" content=\"article\">")
                .contains("<meta property=\"og:image\" content=\"http://localhost:9000/blog-images/images/2026/10/a.webp\">")
                .contains("<meta property=\"article:published_time\" content=\"2026-10-01T00:00:00Z\">")
                .doesNotContain("article:modified_time").doesNotContain("noindex")
                .contains("조회 1.2만").contains("♥ 7").contains("댓글 3").contains("2026.10.01")
                .contains("href=\"/@detailmeta\"").contains("블로그 가기")
                .contains("href=\"/login?redirect=/@detailmeta/posts/" + id + "\"")
                .doesNotContain("class=\"like-button\"").doesNotContain("id=\"visibility-select\"")
                .contains("data-view-post-id=\"" + id + "\"");
        String description = html.substring(html.indexOf("<meta name=\"description\" content=\"") + 34);
        assertThat(description.substring(0, description.indexOf('"')).codePointCount(0, description.indexOf('"'))).isEqualTo(160);
    }

    @Test
    void ownerAndReaderViewsDiffer() throws Exception {
        long me = writer(members, "detailowner");
        long reader = writer(members, "detailreader");
        long id = post(me, "보는 사람별");
        jdbc.update("UPDATE post SET edited_at = '2026-10-03T00:00:00Z' WHERE id = ?", id);
        String mine = mockMvc.perform(get("/@detailowner/posts/{id}", id).with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("id=\"visibility-select\"").contains("data-trash-post").contains(">수정</a>")
                .doesNotContain("class=\"like-button\"").doesNotContain("class=\"follow-button\"")
                .doesNotContain("data-view-post-id").contains("수정됨").contains("10월 3일")
                .contains("article:modified_time");
        String theirs = mockMvc.perform(get("/@detailowner/posts/{id}", id).with(TestAuth.member(reader)))
                .andReturn().getResponse().getContentAsString();
        assertThat(theirs).contains("class=\"like-button\"").contains("class=\"report-button\"")
                .contains("data-follow-handle=\"detailowner\"").doesNotContain("id=\"visibility-select\"");
        String admin = mockMvc.perform(get("/@detailowner/posts/{id}", id).with(TestAuth.admin(reader)))
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).doesNotContain("data-view-post-id");
    }

    @Test
    void privatePostForOwnerIsNoindexNoStoreWithPublishedDate() throws Exception {
        long me = writer(members, "detailpriv");
        long id = post(me, "비공개");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', first_public_at = NULL, published_at = '2026-09-30T00:00:00Z' WHERE id = ?", id);
        String html = mockMvc.perform(get("/@detailpriv/posts/{id}", id).with(TestAuth.member(me)))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<meta name=\"robots\" content=\"noindex\">").doesNotContain("og:title")
                .contains("2026.09.30").contains("🔒 비공개");
    }

    @Test
    void visibilityOnlyChangeDoesNotShowEditedAndTagsAreLinked() throws Exception {
        long me = writer(members, "detailtags");
        long id = post(me, "태그");
        jdbc.update("INSERT INTO tag (name) VALUES ('c#'), ('spring-boot')");
        jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) SELECT ?, id, CASE name WHEN 'spring-boot' THEN 0 ELSE 1 END FROM tag", id);
        String html = mockMvc.perform(get("/@detailtags/posts/{id}", id)).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("수정됨");
        assertThat(html.indexOf("#spring-boot")).isLessThan(html.indexOf("#c#"));
        assertThat(html).contains("href=\"/tags/c%23\"").contains("href=\"/tags/spring-boot\"");
    }

    @Test
    void atMostThreeQueriesAndNoMarkdown() throws Exception {
        long me = writer(members, "detailq");
        long id = post(me, "쿼리");
        org.mockito.Mockito.clearInvocations(jdbc);
        mockMvc.perform(get("/@detailq/posts/{id}", id).with(TestAuth.member(me))).andExpect(status().isOk());
        java.util.List<String> sqls = org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().startsWith("query") && inv.getArguments().length > 0
                        && inv.getArgument(0) instanceof String)
                .map(inv -> (String) inv.getArgument(0)).distinct().toList();
        assertThat(sqls.stream().filter(sql -> sql.contains("FROM post p JOIN member m")).toList()).hasSize(1);
        assertThat(String.join("\n", sqls)).doesNotContain("content_md\n").doesNotContain("p.content_md");
    }
}
