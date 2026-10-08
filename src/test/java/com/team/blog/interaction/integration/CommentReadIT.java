package com.team.blog.interaction.integration;

import static com.team.blog.interaction.integration.CommentTestSupport.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 014: 조회(정렬·커서·답글 3개+더 보기·상태 표시·비노출·특정 댓글부터·SSR) FR-011~FR-021. */
class CommentReadIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com", "Blog#2026ok", true);
    }

    /** 직접 넣기(요청 제한 없이, 시각 지정). */
    private long insert(long post, long author, Long parent, String content, Instant at) {
        return jdbc.queryForObject("""
                INSERT INTO comment (post_id, author_id, parent_id, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, post, author, parent, content, Timestamp.from(at), Timestamp.from(at));
    }

    @Test
    void rootsOldestFirstTwentyPerPageWithThreeRepliesAndMore() throws Exception {
        long author = member("readauthor");
        long a = member("readera");
        long post = posts.published(author, "글", "본문", 1, T);
        List<Long> roots = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            roots.add(insert(post, a, null, "최상위 " + i, T.plus(Duration.ofMinutes(i))));
        }
        for (int i = 0; i < 25; i++) {
            insert(post, author, roots.get(0), "답글 " + i, T.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(i)));
        }
        String first = mockMvc.perform(get("/api/posts/{id}/comments", post))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].content").value("최상위 0"))
                .andExpect(jsonPath("$.items[0].replies.length()").value(3))
                .andExpect(jsonPath("$.items[0].replies[0].content").value("답글 0"))
                .andExpect(jsonPath("$.items[0].replies[0].author.isPostAuthor").value(true))
                .andExpect(jsonPath("$.items[0].replyCount").value(25))
                .andExpect(jsonPath("$.items[0].repliesNextCursor").exists())
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.nextCursor");
        mockMvc.perform(get("/api/posts/{id}/comments", post).param("cursor", next))
                .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.items[0].content").value("최상위 20"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        String repliesCursor = JsonPath.read(first, "$.items[0].repliesNextCursor");
        String more = mockMvc.perform(get("/api/comments/{id}/replies", roots.get(0)).param("cursor", repliesCursor))
                .andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.items[0].content").value("답글 3"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/comments/{id}/replies", roots.get(0)).param("cursor", (String) JsonPath.read(more, "$.nextCursor")))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void statesHideContentAndAuthor() throws Exception {
        long author = member("stateauthor");
        long a = member("statea");
        long gone = member("stategone");
        long post = posts.published(author, "글", "본문", 1, T);
        long normal = insert(post, a, null, "정상", T);
        long deleted = insert(post, a, null, "지운 내용", T.plusSeconds(1));
        insert(post, a, deleted, "남은 답글", T.plusSeconds(2));
        jdbc.update("UPDATE comment SET deleted_at = now(), content = '' WHERE id = ?", deleted);
        long hidden = insert(post, a, null, "숨긴 내용", T.plusSeconds(3));
        jdbc.update("UPDATE comment SET hidden_at = now() WHERE id = ?", hidden);
        long withdrawn = insert(post, gone, null, "탈퇴 회원 내용", T.plusSeconds(4));
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", gone);

        String asOther = mockMvc.perform(get("/api/posts/{id}/comments", post)).andReturn().getResponse().getContentAsString();
        assertThat(asOther).contains("정상").doesNotContain("지운 내용").doesNotContain("숨긴 내용").doesNotContain("탈퇴 회원 내용")
                .doesNotContain("stategone");
        assertThat((String) JsonPath.read(asOther, "$.items[1].state")).isEqualTo("DELETED");
        assertThat((String) JsonPath.read(asOther, "$.items[2].state")).isEqualTo("HIDDEN");
        assertThat((String) JsonPath.read(asOther, "$.items[3].state")).isEqualTo("WITHDRAWN_AUTHOR");
        String asWriter = mockMvc.perform(get("/api/posts/{id}/comments", post).with(TestAuth.member(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(asWriter).contains("숨긴 내용");
        assertThat((Boolean) JsonPath.read(asWriter, "$.items[2].canEdit")).isFalse();
        assertThat((Boolean) JsonPath.read(asWriter, "$.items[2].canDelete")).isTrue();
        assertThat(normal).isPositive();
    }

    @Test
    void aroundStartsAtTargetRootAndExpandsReplies() throws Exception {
        long author = member("aroundauthor");
        long a = member("arounda");
        long post = posts.published(author, "글", "본문", 1, T);
        List<Long> roots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            roots.add(insert(post, a, null, "최상위 " + i, T.plus(Duration.ofMinutes(i))));
        }
        long root = roots.get(25);
        long fifth = 0;
        for (int i = 0; i < 6; i++) {
            long r = insert(post, a, root, "답글 " + i, T.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(i)));
            if (i == 4) {
                fifth = r;
            }
        }
        mockMvc.perform(get("/api/posts/{id}/comments", post).param("around", String.valueOf(fifth)))
                .andExpect(jsonPath("$.items[0].content").value("최상위 25"))
                .andExpect(jsonPath("$.items[0].replies.length()").value(5))
                .andExpect(jsonPath("$.prevCursor").exists());
        // 없는·다른 글 댓글이면 첫 페이지
        mockMvc.perform(get("/api/posts/{id}/comments", post).param("around", "999999"))
                .andExpect(jsonPath("$.items[0].content").value("최상위 0"));
        String html = mockMvc.perform(get("/@aroundauthor/posts/{id}", post).param("comment", String.valueOf(fifth)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"comment-" + fifth + "\"").contains("이전 댓글 보기")
                .contains("data-comment-target=\"" + fifth + "\"");
    }

    @Test
    void ssrFirstPageFormAndEscaping() throws Exception {
        long author = member("ssrcmt");
        long a = member("ssrcmta");
        long post = posts.published(author, "글", "본문", 1, T);
        jdbc.update("UPDATE post SET comment_count = 1 WHERE id = ?", post);
        insert(post, a, null, "<script>alert(1)</script>\n둘째 줄 https://x.dev", T);
        String guest = mockMvc.perform(get("/@ssrcmt/posts/{id}", post)).andReturn().getResponse().getContentAsString();
        assertThat(guest).contains("댓글 1").contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<a href=\"https://x.dev")
                .contains("로그인하고 댓글 쓰기").doesNotContain("id=\"comment-form\"");
        String member = mockMvc.perform(get("/@ssrcmt/posts/{id}", post).with(TestAuth.member(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(member).contains("id=\"comment-form\"").contains("comment-edit").doesNotContain("comment-report");
        long unverified = members.localMember("ssrunver", "미인증", "ssrunver@example.com", "Blog#2026ok", false);
        assertThat(mockMvc.perform(get("/@ssrcmt/posts/{id}", post).with(TestAuth.member(unverified)))
                .andReturn().getResponse().getContentAsString()).contains("이메일 인증을 마치면 댓글을 쓸 수 있어요");
        long empty = posts.published(author, "빈 글", "본문", 1, T);
        assertThat(mockMvc.perform(get("/@ssrcmt/posts/{id}", empty)).andReturn().getResponse().getContentAsString())
                .contains("첫 댓글을 남겨 보세요");
        mockMvc.perform(get("/api/posts/{id}/comments", post)).andExpect(header().string("Cache-Control", "private, no-store"));
        // 스크립트 없는 작성 폼
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/@ssrcmt/posts/{id}/comments", post)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(TestAuth.member(a)).param("content", "폼으로 쓴 댓글"))
                .andExpect(status().isSeeOther());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE content = '폼으로 쓴 댓글'", Integer.class)).isEqualTo(1);
        mockMvc.perform(create(post, "API", null).with(TestAuth.member(a))).andExpect(status().isCreated());
    }
}
