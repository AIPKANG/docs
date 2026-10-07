package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.RerenderService;
import com.team.blog.post.markdown.ContentRenderer;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 007 T414·SC-006: 이전 렌더링 버전의 발행 글만 다시 렌더링하고 수정 시각·편집 버전은 그대로. */
class RerenderIT extends IntegrationTestBase {

    @Autowired
    RerenderService rerender;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rerendersOldPublishedPostsOnlyAndKeepsTimes() {
        long me = writer(members, "rerenderer");
        long old = posts.published(me, "옛 글", "# 제목\n\n<b>글자</b> 본문", 4, Instant.parse("2026-10-01T00:00:00Z"));
        jdbc.update("UPDATE post SET render_version = 0, edited_at = '2026-10-02T00:00:00Z', updated_at = '2026-10-02T00:00:00Z' WHERE id = ?", old);
        long current = posts.published(me, "새 글", "그대로", 1, Instant.parse("2026-10-01T00:00:00Z"));
        long draft = posts.draft(me, "임시", "# 임시", 0);
        jdbc.update("UPDATE post SET render_version = 0 WHERE id = ?", draft);
        Map<String, Object> before = posts.post(old);

        assertThat(rerender.runAll()).isEqualTo(1);
        Map<String, Object> after = posts.post(old);
        assertThat(after.get("render_version")).isEqualTo(ContentRenderer.RENDER_VERSION);
        assertThat((String) after.get("content_html")).contains("<h2 id=\"h-제목\">제목</h2>").contains("&lt;b&gt;글자&lt;/b&gt;");
        assertThat(after.get("excerpt")).isEqualTo("제목 <b>글자</b> 본문");
        for (String unchanged : new String[] {"edited_at", "edit_version", "updated_at", "published_at", "first_public_at", "title"}) {
            assertThat(after.get(unchanged)).as(unchanged).isEqualTo(before.get(unchanged));
        }
        assertThat(posts.post(current).get("content_html")).isEqualTo("<p>발행본</p>");
        assertThat(posts.post(draft).get("render_version")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post WHERE status='PUBLISHED' AND render_version < ?",
                Integer.class, ContentRenderer.RENDER_VERSION)).isZero();
    }

    @Test
    void processesInBatchesOfHundred() {
        long me = writer(members, "rerenderbulk");
        for (int i = 0; i < 130; i++) {
            long id = posts.published(me, "글" + i, "본문 " + i, 1, Instant.parse("2026-10-01T00:00:00Z"));
            jdbc.update("UPDATE post SET render_version = 0 WHERE id = ?", id);
        }
        assertThat(rerender.runBatch()).isEqualTo(100);
        assertThat(rerender.runBatch()).isEqualTo(30);
        assertThat(rerender.runBatch()).isZero();
    }
}
