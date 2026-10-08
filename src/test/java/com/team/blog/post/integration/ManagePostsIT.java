package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 011 T1106: 내 글 관리 세 탭(FR-001~FR-015). */
class ManagePostsIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private void touch(long id, Instant updated) {
        jdbc.update("UPDATE post SET updated_at = ? WHERE id = ?", Timestamp.from(updated), id);
    }

    @Test
    void tabsShowOnlyMyPostsWithCountsAndDefaultIsDrafts() throws Exception {
        long me = writer(members, "manageme");
        long other = writer(members, "manageoth");
        long d1 = posts.draft(me, "", "", 0);
        long d2 = posts.draft(me, "<i>임시</i>", "", 0);
        touch(d1, T);
        touch(d2, T.plusSeconds(60));
        long pub = posts.published(me, "공개 발행", "본문", 2, T);
        long priv = posts.published(me, "비공개 발행", "본문", 2, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE', edited_at = now(), view_count = 12345, hidden_at = now() WHERE id = ?", priv);
        posts.workingCopy(pub, "작업본", "", 3);
        long trashed = posts.published(me, "버린 글", "본문", 1, T);
        posts.trash(trashed);
        posts.draft(other, "남의 임시글", "", 0);

        String drafts = mockMvc.perform(get("/manage/posts").with(TestAuth.member(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(drafts).contains("임시글 2").contains("발행 글 2").contains("휴지통 1")
                .contains("(제목 없음)").contains("&lt;i&gt;임시&lt;/i&gt;").contains("마지막 저장").contains("이어 쓰기")
                .doesNotContain("공개 발행").doesNotContain("남의 임시글").doesNotContain("🌐");
        assertThat(drafts.indexOf("&lt;i&gt;임시")).isLessThan(drafts.indexOf("(제목 없음)"));

        String published = mockMvc.perform(get("/manage/posts").param("tab", "published").with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(published).contains("공개 발행").contains("비공개 발행").contains("수정 중").contains("이어서 수정")
                .contains("변경 취소").contains(">숨김</span>").contains("조회 1.2만").contains("수정됨")
                .contains(">공개<").contains(">비공개<").doesNotContain("버린 글");
        String onlyPrivate = mockMvc.perform(get("/manage/posts").param("tab", "published").param("visibility", "private")
                .with(TestAuth.member(me))).andReturn().getResponse().getContentAsString();
        assertThat(onlyPrivate).contains("비공개 발행").doesNotContain(">공개 발행</a>");

        String trash = mockMvc.perform(get("/manage/posts").param("tab", "trash").with(TestAuth.member(me)))
                .andReturn().getResponse().getContentAsString();
        assertThat(trash).contains("휴지통의 글은 30일 뒤 자동으로 완전히 삭제돼요").contains("버린 글")
                .contains("(발행 글이었음)").contains("일 뒤 완전 삭제").contains("복구").contains("영구 삭제");
        mockMvc.perform(get("/manage/posts")).andExpect(status().isSeeOther());
        assertThat(d1 + pub + priv).isPositive();
    }

    @Test
    void twentyPerPageWithCursorAndNoBodyColumns() throws Exception {
        long me = writer(members, "managepage");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            long id = posts.draft(me, "임시 " + i, "본문", 0);
            touch(id, T.plus(Duration.ofMinutes(i)));
            ids.add(id);
        }
        String first = mockMvc.perform(get("/api/me/posts").param("tab", "drafts").param("size", "100").with(TestAuth.member(me)))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].title").value("임시 44"))
                .andExpect(jsonPath("$.counts.drafts").value(45))
                .andReturn().getResponse().getContentAsString();
        assertThat(first).doesNotContain("contentMd").doesNotContain("본문");
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        String json = first;
        do {
            List<Number> page = JsonPath.read(json, "$.items[*].id");
            page.forEach(n -> seen.add(n.longValue()));
            cursor = JsonPath.read(json, "$.nextCursor");
            if (cursor != null) {
                json = mockMvc.perform(get("/api/me/posts").param("tab", "drafts").param("cursor", cursor).with(TestAuth.member(me)))
                        .andExpect(jsonPath("$.counts").doesNotExist())
                        .andReturn().getResponse().getContentAsString();
            }
        } while (cursor != null);
        assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(ids.reversed());
        mockMvc.perform(get("/api/me/posts").param("cursor", "bad!").with(TestAuth.member(me))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/posts")).andExpect(status().isUnauthorized());
    }
}
