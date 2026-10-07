package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 006 T605: 공용 목록 조건(FR-009~FR-011). 목록 화면(009·013·020)은 이 조건만 쓴다. */
class PublicListingConditionIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    PostAccessPolicy accessPolicy;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyPublishedPublicNotTrashedFromActiveAuthors() {
        long a = writer(members, "lista");
        long b = writer(members, "listb");
        long visible = posts.published(a, "보임", "본문", 1, T);
        long privatePost = posts.published(a, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", privatePost);
        posts.draft(a, "임시", "본문", 0);
        long trashed = posts.published(a, "휴지통", "본문", 1, T);
        posts.trash(trashed);
        posts.published(b, "탈퇴 작성자", "본문", 1, T);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", b);

        String condition = accessPolicy.publicListingCondition("p", "m");
        assertThat(jdbc.queryForList("SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE " + condition, Long.class))
                .containsExactly(visible);
    }
}
