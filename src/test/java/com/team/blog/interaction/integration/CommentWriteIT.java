package com.team.blog.interaction.integration;

import static com.team.blog.interaction.integration.CommentTestSupport.create;
import static com.team.blog.interaction.integration.CommentTestSupport.edit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 014: 작성·답글 구조·검사 순서·중복·제한·수정(FR-001~FR-010, FR-022, FR-023, FR-030, FR-031). */
class CommentWriteIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com", "Blog#2026ok", true);
    }

    private long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM comment WHERE id = ?", id);
    }

    @Test
    void oneLevelStructureAndReplyTarget() throws Exception {
        long author = member("cmtauthor");
        long a = member("cmta");
        long b = member("cmtb");
        long post = posts.published(author, "글", "본문", 1, T);
        long root = id(mockMvc.perform(create(post, "최상위", null).with(TestAuth.member(a)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("NORMAL"))
                .andReturn().getResponse().getContentAsString());
        long reply = id(mockMvc.perform(create(post, "최상위에 바로 답글", root).with(TestAuth.member(b)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(row(reply)).containsEntry("parent_id", root);
        assertThat(row(reply).get("reply_to_member_id")).isNull();
        // 답글에 답하면 같은 최상위 아래 + 대상 회원 기록
        long replyToReply = id(mockMvc.perform(create(post, "답글의 답글", reply).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.replyTo.nickname").value("cmtb")).andReturn().getResponse().getContentAsString());
        assertThat(row(replyToReply)).containsEntry("parent_id", root).containsEntry("reply_to_member_id", b);
        // 내 답글에 내가 단 답글은 대상 없음
        long self = id(mockMvc.perform(create(post, "내 답글에 내가", replyToReply).with(TestAuth.member(a)))
                .andReturn().getResponse().getContentAsString());
        assertThat(row(self).get("reply_to_member_id")).isNull();
        assertThat(posts.post(post).get("comment_count")).isEqualTo(4);
        // 다른 글의 댓글·삭제된 댓글은 대상이 될 수 없다
        long otherPost = posts.published(author, "다른 글", "본문", 1, T);
        mockMvc.perform(create(otherPost, "엉뚱한 대상", root).with(TestAuth.member(a)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPLY_TARGET_UNAVAILABLE"));
        jdbc.update("UPDATE comment SET hidden_at = now() WHERE id = ?", reply);
        mockMvc.perform(create(post, "숨긴 댓글에 답글", reply).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.code").value("REPLY_TARGET_UNAVAILABLE"));
    }

    @Test
    void checkOrderAndPermissions() throws Exception {
        long author = member("cmtorder");
        long a = member("cmtordera");
        long unverified = members.localMember("cmtunver", "미인증", "cmtunver@example.com", "Blog#2026ok", false);
        long post = posts.published(author, "글", "본문", 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long draft = posts.draft(author, "임시", "본문", 0);
        long trashed = posts.published(author, "휴지통", "본문", 1, T);
        posts.trash(trashed);

        mockMvc.perform(create(post, "x", null)).andExpect(status().isUnauthorized());
        mockMvc.perform(create(post, "x", null).with(TestAuth.member(unverified))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // 내용 검사가 글 판정보다 먼저(FR-003)
        mockMvc.perform(create(priv, "   ", null).with(TestAuth.member(a))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mockMvc.perform(create(post, "가".repeat(1001), null).with(TestAuth.member(a)))
                .andExpect(jsonPath("$.code").value("COMMENT_TOO_LONG"));
        String notFound = null;
        for (long target : new long[] {priv, draft, trashed, 999999}) {
            String body = mockMvc.perform(create(target, "x", null).with(TestAuth.member(a)))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            if (notFound == null) {
                notFound = body;
            }
            assertThat(body).isEqualTo(notFound);
        }
        // 비공개 글도 작성자는 쓸 수 있다
        mockMvc.perform(create(priv, "내 비공개 글에", null).with(TestAuth.member(author))).andExpect(status().isCreated());
    }

    @Test
    void duplicateWithinTenSecondsReturnsFirstAndRateLimit() throws Exception {
        long author = member("cmtdup");
        long a = member("cmtdupa");
        long post = posts.published(author, "글", "본문", 1, T);
        long first = id(mockMvc.perform(create(post, "같은 내용", null).with(TestAuth.member(a)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long again = id(mockMvc.perform(create(post, "같은 내용", null).with(TestAuth.member(a)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(again).isEqualTo(first);
        assertThat(posts.post(post).get("comment_count")).isEqualTo(1);
        // 요청 횟수 검사는 중복 판정보다 먼저라 위 두 요청도 센다(FR-003): 8개 더 → 10개
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(create(post, "다른 내용 " + i, null).with(TestAuth.member(a))).andExpect(status().isCreated());
        }
        mockMvc.perform(create(post, "11번째", null).with(TestAuth.member(a))).andExpect(status().isTooManyRequests());
    }

    @Test
    void editRules() throws Exception {
        long author = member("cmtedit");
        long a = member("cmtedita");
        long b = member("cmteditb");
        long post = posts.published(author, "글", "본문", 1, T);
        long c = id(mockMvc.perform(create(post, "원래", null).with(TestAuth.member(a))).andReturn().getResponse().getContentAsString());
        mockMvc.perform(edit(c, "남이 고침").with(TestAuth.member(b))).andExpect(status().isNotFound());
        mockMvc.perform(edit(c, "글 주인이 고침").with(TestAuth.member(author))).andExpect(status().isNotFound());
        Object before = row(c).get("updated_at");
        mockMvc.perform(edit(c, "  원래  ").with(TestAuth.member(a))).andExpect(status().isOk()).andExpect(jsonPath("$.edited").value(false));
        assertThat(row(c).get("updated_at")).isEqualTo(before);
        clock.advance(java.time.Duration.ofMinutes(5));
        mockMvc.perform(edit(c, "고친 내용").with(TestAuth.member(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 내용")).andExpect(jsonPath("$.edited").value(true));
        assertThat(posts.post(post).get("comment_count")).isEqualTo(1);
        jdbc.update("UPDATE comment SET hidden_at = now() WHERE id = ?", c);
        mockMvc.perform(edit(c, "숨김 뒤 수정").with(TestAuth.member(a))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_HIDDEN"));
        assertThat(redis).isNotNull();
    }
}
