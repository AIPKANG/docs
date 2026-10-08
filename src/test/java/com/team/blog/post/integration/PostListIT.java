package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostCard;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 009 T908: 홈 목록(US1, FR-001~FR-008, FR-023). */
class PostListIT extends IntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    @MockitoSpyBean
    JdbcTemplate jdbc;

    @Autowired
    PostListQuery listQuery;

    /** {@code n}개 공개 글, i번째는 T0 + i시간에 공개. */
    private List<Long> publishMany(long author, int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long id = posts.published(author, "글 " + i, "본문", 1, T0.plus(Duration.ofHours(i)));
            jdbc.update("UPDATE post SET excerpt = ?, comment_count = ?, like_count = ? WHERE id = ?", "요약 " + i, i, 2 * i, id);
            ids.add(id);
        }
        return ids;
    }

    private List<Long> ids(CardPage page) {
        return page.items().stream().map(PostCard::id).toList();
    }

    @Test
    void pageSizeNewestFirstAndEndDetected() {
        // 한 쪽 카드 수: 공통 9, 강성찬 개인 확장 12(PostListQuery.PAGE_SIZE)
        long a = writer(members, "listauthor");
        List<Long> ids = publishMany(a, 26);
        CardPage first = listQuery.feed(null);
        assertThat(ids(first)).containsExactlyElementsOf(ids.reversed().subList(0, 12));
        assertThat(first.nextCursor()).isNotNull();
        CardPage second = listQuery.feed(first.nextCursor());
        assertThat(ids(second).get(0)).isEqualTo(ids.get(13));
        CardPage third = listQuery.feed(second.nextCursor());
        assertThat(ids(third)).containsExactly(ids.get(1), ids.get(0));
        assertThat(third.nextCursor()).isNull();
        PostCard card = first.items().get(0);
        assertThat(card.url()).isEqualTo("/@listauthor/posts/" + ids.get(25));
        assertThat(card.excerpt()).isEqualTo("요약 25");
        assertThat(card.commentCount()).isEqualTo(25);
        assertThat(card.likeCount()).isEqualTo(50);
        assertThat(card.author().handle()).isEqualTo("listauthor");
    }

    @Test
    void exactlyOnePageHasNoNextCursorAndSameTimeOrdersById() {
        long a = writer(members, "ninepost");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(posts.published(a, "같은 시각 " + i, "본문", 1, T0));
        }
        CardPage page = listQuery.feed(null);
        assertThat(page.nextCursor()).isNull();
        assertThat(ids(page)).containsExactlyElementsOf(ids.reversed());
    }

    @Test
    void changesWhileBrowsingNeverDuplicateOrSkip() {
        long a = writer(members, "browsing");
        List<Long> ids = publishMany(a, 26);
        CardPage first = listQuery.feed(null);
        // 보는 도중: 새 글 공개, 첫 페이지 글 삭제, 다음 페이지 글 비공개, 다시 발행(시각 유지)
        posts.published(a, "새 글", "본문", 1, T0.plus(Duration.ofDays(30)));
        posts.trash(ids.get(25));
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", ids.get(9));
        jdbc.update("UPDATE post SET edited_at = now(), title = '고친 제목' WHERE id = ?", ids.get(8));
        List<Long> seen = new ArrayList<>(ids(first));
        String cursor = first.nextCursor();
        while (cursor != null) {
            CardPage page = listQuery.feed(cursor);
            seen.addAll(ids(page));
            cursor = page.nextCursor();
        }
        List<Long> expected = new ArrayList<>(ids.reversed());
        expected.remove(ids.get(9));
        assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
    }

    @Test
    void onlyPublicPublishedNotTrashedFromActiveAuthors() {
        long a = writer(members, "condauthor");
        long b = writer(members, "leaving");
        long visible = posts.published(a, "보임", "본문", 1, T0);
        long priv = posts.published(a, "비공개", "본문", 1, T0);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        posts.draft(a, "임시", "본문", 0);
        posts.published(b, "탈퇴 작성자", "본문", 1, T0);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", b);
        assertThat(ids(listQuery.feed(null))).containsExactly(visible);
    }

    @Test
    void oneQueryPerPageAndNoBodyColumns() {
        long a = writer(members, "queries");
        publishMany(a, 12);
        clearInvocations(jdbc);
        listQuery.feed(null);
        // 스파이는 JdbcTemplate 안의 위임 호출까지 세므로, 서로 다른 SQL 문장 수로 본다
        long statements = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().startsWith("query") && inv.getArguments().length > 0
                        && inv.getArgument(0) instanceof String)
                .map(inv -> (String) inv.getArgument(0)).distinct().count();
        assertThat(statements).isEqualTo(1);
        String sql = (String) mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().startsWith("query")).findFirst().orElseThrow().getArgument(0);
        assertThat(sql).doesNotContain("content_md").doesNotContain("content_html");
    }

    @Test
    void apiAndSsrAndInvalidCursor() throws Exception {
        long a = writer(members, "apiuser");
        List<Long> ids = publishMany(a, 13);
        String json = mockMvc.perform(get("/api/posts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.items[0].title").value("글 12"))
                .andExpect(jsonPath("$.items[0].author.nickname").value("apiuser"))
                .andExpect(jsonPath("$.items[0].firstPublicAt").exists())
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(json, "$.nextCursor");
        mockMvc.perform(get("/api/posts").param("cursor", next))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(get("/api/posts").param("cursor", "garbage!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

        String home = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("글 12").contains("href=\"?cursor=" + next + "\"").doesNotContain("글 0<");
        String page2 = mockMvc.perform(get("/").param("cursor", next)).andReturn().getResponse().getContentAsString();
        assertThat(page2).contains("글 0").contains("모든 글을 다 봤어요").contains("처음부터 보기");
        assertThat(mockMvc.perform(get("/").param("cursor", "garbage!")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).contains("글 12");
    }

    @Test
    void emptyStates() throws Exception {
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                .contains("아직 올라온 글이 없어요").contains("href=\"/login\"");
        long me = writer(members, "emptyhome");
        assertThat(mockMvc.perform(get("/").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("아직 올라온 글이 없어요").contains("글쓰기");
    }
}
