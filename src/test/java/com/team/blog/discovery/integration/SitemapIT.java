package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** sitemap·robots(02 §6, 06 §3): 공개 글만, 비공개·친구 공개·삭제 글은 없다. */
class SitemapIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyPublicPostsAndTheirBlogsAreListed() throws Exception {
        long a = members.localMember("smauthor", "smauthor", "smauthor@example.com", "Blog#2026ok", true);
        Instant t = Instant.parse("2026-10-09T00:00:00Z");
        long open = posts.published(a, "공개", "본문", 1, t);
        long priv = posts.published(a, "비공개", "본문", 1, t);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long friends = posts.published(a, "친구", "본문", 1, t);
        jdbc.update("UPDATE post SET visibility = 'FRIENDS', first_public_at = NULL WHERE id = ?", friends);
        long trashed = posts.published(a, "휴지통", "본문", 1, t);
        posts.trash(trashed);
        String xml = mockMvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(xml).contains("/@smauthor/posts/" + open + "</loc>").contains("/@smauthor</loc>")
                .doesNotContain("/posts/" + priv + "<").doesNotContain("/posts/" + friends + "<").doesNotContain("/posts/" + trashed + "<");
        assertThat(mockMvc.perform(get("/robots.txt")).andReturn().getResponse().getContentAsString())
                .contains("Sitemap: ").contains("/sitemap.xml").contains("Disallow: /api/");
    }
}
