package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;
import com.team.blog.interaction.application.InteractionPurgeSteps;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** 028 답글 무제한 깊이(강성찬 개인 확장, 21 §13): 바로 위 댓글에 답글, 3단계까지 펼치고 더 깊으면 접기. */
@TestPropertySource(properties = "blog.comment.max-depth=0")
class NestedRepliesIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-09T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InteractionPurgeSteps.Comments commentPurge;

    private final JsonMapper json = JsonMapper.builder().build();

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private long write(long who, long postId, String content, Long replyTo) throws Exception {
        String body = replyTo == null ? "{\"content\":\"" + content + "\"}"
                : "{\"content\":\"" + content + "\",\"replyToCommentId\":" + replyTo + "}";
        String res = mockMvc.perform(post("/api/posts/{id}/comments", postId).with(csrf()).with(TestAuth.member(who))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private Long parent(long id) {
        return jdbc.queryForObject("SELECT parent_id FROM comment WHERE id = ?", Long.class, id);
    }

    private int commentCount(long postId) {
        return jdbc.queryForObject("SELECT comment_count FROM post WHERE id = ?", Integer.class, postId);
    }

    @Test
    void repliesNestUnderTheirDirectParentAndFoldBelowThreeLevels() throws Exception {
        clock.set(T);
        long author = member("nrauthor");
        long a = member("nra");
        long b = member("nrb");
        long postId = posts.published(author, "대화 글", "본문", 1, T);
        long l1 = write(a, postId, "첫째-단계", null);
        long l2 = write(b, postId, "둘째-단계", l1);
        long l3 = write(a, postId, "셋째-단계", l2);
        long l4 = write(b, postId, "넷째-단계", l3);
        long l5 = write(a, postId, "다섯째-단계", l4);
        assertThat(parent(l2)).isEqualTo(l1);
        assertThat(parent(l3)).isEqualTo(l2);
        assertThat(parent(l5)).isEqualTo(l4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE reply_to_member_id IS NOT NULL", Integer.class)).isZero();
        assertThat(commentCount(postId)).isEqualTo(5);
        // 답글 알림은 바로 위 작성자에게
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE type = 'REPLY' AND comment_id = ? AND receiver_id = ?",
                Integer.class, l4, a)).isEqualTo(1);
        // 화면: 3단계까지 펼침, 4단계부터 접힘. @대상 표시 없음
        String html = mockMvc.perform(get("/@nrauthor/posts/" + postId)).andReturn().getResponse().getContentAsString();
        int fold = html.indexOf("replies-fold");
        assertThat(fold).isPositive();
        assertThat(html.indexOf("셋째-단계")).isLessThan(fold);
        assertThat(html.indexOf("넷째-단계")).isGreaterThan(fold);
        assertThat(html).contains("다섯째-단계").doesNotContain("에게</p>");
        // 같은 단계의 답글은 처음 3개, 나머지는 "답글 N개 더보기"
        for (int i = 0; i < 5; i++) {
            write(b, postId, "같은단계" + i, l1);
        }
        html = mockMvc.perform(get("/@nrauthor/posts/" + postId)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("답글 3개 더보기"); // l1 아래 6개(l2 + 5) 중 3개 접힘
        // 특정 답글로 들어오면 그 대화의 최상위 페이지
        assertThat(mockMvc.perform(get("/@nrauthor/posts/" + postId).param("comment", String.valueOf(l5))).andReturn()
                .getResponse().getContentAsString()).contains("다섯째-단계");
    }

    @Test
    void deletingKeepsPlaceholdersOnlyWhileRepliesRemain() throws Exception {
        clock.set(T);
        long author = member("nrdel");
        long a = member("nrdela");
        long postId = posts.published(author, "지우기 글", "본문", 1, T);
        long l1 = write(a, postId, "하나", null);
        long l2 = write(a, postId, "둘", l1);
        long l3 = write(a, postId, "셋", l2);
        mockMvc.perform(delete("/api/comments/{id}", l2).with(csrf()).with(TestAuth.member(a))).andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM comment WHERE id = ?", Boolean.class, l2)).isTrue();
        assertThat(commentCount(postId)).isEqualTo(2);
        String html = mockMvc.perform(get("/@nrdel/posts/" + postId)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("삭제된 댓글이에요").contains("셋");
        // 마지막 답글을 지우면 위의 빈 자리도 정리된다(l1은 내용이 있으므로 남음)
        mockMvc.perform(delete("/api/comments/{id}", l3).with(csrf()).with(TestAuth.member(a))).andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE post_id = ?", Integer.class, postId)).isEqualTo(1);
        assertThat(commentCount(postId)).isEqualTo(1);
    }

    @Test
    void withdrawalKeepsOthersRepliesUnderTheWithdrawnMembersComment() throws Exception {
        clock.set(T);
        long author = member("nrwd");
        long leaving = member("nrleaving");
        long staying = member("nrstaying");
        long postId = posts.published(author, "탈퇴 글", "본문", 1, T);
        long top = write(staying, postId, "남는 최상위", null);
        long mid = write(leaving, postId, "떠나는 사람 답글", top);
        long below = write(staying, postId, "그 아래 남는 답글", mid);
        long leaf = write(leaving, postId, "떠나는 사람 잎", top);
        commentPurge.purge(leaving);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ?", Integer.class, below)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM comment WHERE id = ?", Boolean.class, mid)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ?", Integer.class, leaf)).isZero();
        assertThat(commentCount(postId)).isEqualTo(2);
    }
}
