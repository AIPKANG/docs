package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.discovery.application.SearchService;
import com.team.blog.discovery.application.SearchTerms;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** 020 검색. 최근창을 작게 해 인덱스 단계도 함께 검사한다. */
@TestPropertySource(properties = "blog.search.recent-window=3")
class SearchIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SearchService search;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com",
                "Blog#2026ok", true);
    }

    private long post(long author, String title, String body, int minutes) {
        return posts.published(author, title, body, 1, T.plusSeconds(60L * minutes));
    }

    private void tag(long post, String name) {
        Long id = jdbc.query("SELECT id FROM tag WHERE name = ?", (rs, n) -> rs.getLong(1), name).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("INSERT INTO tag (name) VALUES (?) RETURNING id", Long.class, name));
        jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, 0)", post, id);
    }

    private List<Long> ids(SearchService.SearchPage page) {
        return page.items().stream().map(SearchService.SearchCard::id).toList();
    }

    @Test
    void stagesOrderAndRules() {
        long a = member("srauthor");
        long inTitle = post(a, "JPA 트랜잭션 정리", "본문", 1);
        long inTitleOld = post(a, "트랜잭션 이야기", "본문", 0);
        long inTag = post(a, "다른 제목", "본문", 3);
        tag(inTag, "트랜잭션");
        long inBody = post(a, "또 다른 제목", "코드 안에도 트랜잭션 처리가 있다", 5);
        long none = post(a, "관계없음", "내용", 6);
        SearchService.SearchPage rel = search.posts(SearchTerms.parse("트랜잭션"), false, null, null);
        assertThat(ids(rel)).containsExactly(inTitle, inTitleOld, inTag, inBody);
        SearchService.SearchPage latest = search.posts(SearchTerms.parse("트랜잭션"), true, null, null);
        assertThat(ids(latest)).containsExactly(inBody, inTag, inTitle, inTitleOld);
        // AND, 대소문자, 한글 일부(3글자 이상), 1글자 무시
        assertThat(ids(search.posts(SearchTerms.parse("jpa 랜잭션 a"), false, null, null))).containsExactly(inTitle);
        // 2글자는 제목·태그만 + 안내
        long twoInBody = post(a, "제목만", "정리 정리 정리", 7);
        SearchService.SearchPage two = search.posts(SearchTerms.parse("정리"), false, null, null);
        assertThat(ids(two)).containsExactly(inTitle).doesNotContain(twoInBody);
        assertThat(two.notice()).isEqualTo(SearchService.NOTICE_TWO_CHAR);
        // % _ \ 는 글자 그대로
        long percent = post(a, "할인 100% 정리", "본문", 8);
        long plain100 = post(a, "1000원 이야기", "본문", 9);
        long under = post(a, "snake_case 규칙", "본문", 10);
        long underNot = post(a, "snakeXcase 규칙", "본문", 11);
        assertThat(ids(search.posts(SearchTerms.parse("100%"), false, null, null))).containsExactly(percent).doesNotContain(plain100);
        assertThat(ids(search.posts(SearchTerms.parse("e_c"), false, null, null))).containsExactly(under).doesNotContain(underNot);
        assertThat(SearchTerms.parse("  a  ").empty()).isTrue();
        assertThat(none).isPositive();
    }

    @Test
    void onlyPublicListedPostsAndBlogScope() throws Exception {
        long a = member("srvis");
        long b = member("srvisb");
        long pub = post(a, "공개 검색어글", "본문", 1);
        long priv = post(a, "비공개 검색어글", "본문", 2);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long trash = post(a, "휴지통 검색어글", "본문", 3);
        posts.trash(trash);
        long hidden = post(a, "숨김 검색어글", "본문", 4);
        jdbc.update("UPDATE post SET hidden_at = now() WHERE id = ?", hidden);
        posts.draft(a, "임시 검색어글", "", 0);
        long other = post(b, "남의 검색어글", "본문", 5);
        long leaving = members.withdrawing("srleave", "srleave", T);
        post(leaving, "탈퇴 검색어글", "본문", 6);
        assertThat(ids(search.posts(SearchTerms.parse("검색어글"), false, null, null))).containsExactly(other, pub);
        assertThat(ids(search.posts(SearchTerms.parse("검색어글"), false, a, null))).containsExactly(pub);
        String blog = mockMvc.perform(get("/@srvis?q=검색어글")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(blog).contains("공개 검색어글").doesNotContain("남의 검색어글").contains("<meta name=\"robots\" content=\"noindex\">");
        mockMvc.perform(get("/api/search/posts?q=검색어글&blog=srvisb")).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void pagingAcrossStagesWithoutDuplicatesUsingIndexPhase() throws Exception {
        long a = member("srpage");
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            expected.add(post(a, "페이지검색 제목" + i, "본문", i));
        }
        for (int i = 0; i < 6; i++) {
            expected.add(post(a, "다른" + i, "본문에 페이지검색 있음", 100 + i));
        }
        for (int i = 0; i < 5; i++) {
            post(a, "무관" + i, "무관한 본문", 200 + i);
        }
        Set<Long> seen = new HashSet<>();
        List<Long> order = new ArrayList<>();
        String cursor = null;
        do {
            SearchService.SearchPage page = search.posts(SearchTerms.parse("페이지검색"), false, null, cursor);
            for (Long id : ids(page)) {
                assertThat(seen.add(id)).isTrue();
                order.add(id);
            }
            cursor = page.nextCursor();
        } while (cursor != null);
        assertThat(seen).isEqualTo(expected);
        // 제목 단계(7개)가 먼저 다 나온다
        assertThat(order.subList(0, 7)).allMatch(id -> ((String) posts.post(id).get("title")).startsWith("페이지검색"));
        mockMvc.perform(get("/api/search/posts?q=페이지검색&cursor=bad")).andExpect(status().isBadRequest());
    }

    @Test
    void snippetEscapesHtmlAndMarksOnlyTerms() throws Exception {
        long a = member("srxss");
        post(a, "스크립트 글", "앞부분 <script>alert(1)</script> 그리고 위험단어 <b>굵게</b> 뒷부분", 1);
        String json = mockMvc.perform(get("/api/search/posts?q=위험단어")).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("&lt;script&gt;alert(1)&lt;/script&gt; 그리고 <mark>위험단어</mark> &lt;b&gt;")
                .contains("\"mark\":true");
        String html = mockMvc.perform(get("/search?q=위험단어")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<mark>위험단어</mark>").doesNotContain("<script>alert(1)").doesNotContain("<b>굵게");
        String longBody = "가".repeat(100) + "목표단어" + "나".repeat(100);
        post(a, "긴 글", longBody, 2);
        String snippet = search.posts(SearchTerms.parse("목표단어"), false, null, null).items().get(0).snippetHtml();
        assertThat(snippet).isEqualTo("…" + "가".repeat(40) + "<mark>목표단어</mark>" + "나".repeat(40) + "…");
    }

    @Test
    void peopleHashTagEmptyStatesAndRateLimit() throws Exception {
        long exact = member("kimdev");
        jdbc.update("UPDATE member SET nickname = '개발자', bio = '첫 줄\n둘째' WHERE id = ?", exact);
        long partial = member("kimdevlog");
        jdbc.update("UPDATE member SET nickname = '개발자로그' WHERE id = ?", partial);
        members.withdrawing("kimdevgone", "개발자탈퇴", T);
        mockMvc.perform(get("/api/search/people?q=개발자")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].handle").value("kimdev")).andExpect(jsonPath("$[0].bio").value("첫 줄"));
        mockMvc.perform(get("/api/search/people?q=kimdevlog")).andExpect(jsonPath("$[0].handle").value("kimdevlog"));
        // #태그: 있으면 태그 목록으로, 없으면 # 뺀 검색
        long p = post(exact, "태그 글", "본문", 1);
        tag(p, "spring");
        mockMvc.perform(get("/search").param("q", "#Spring")).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/tags/spring"));
        String fallback = mockMvc.perform(get("/search").param("q", "#없는태그글")).andReturn().getResponse().getContentAsString();
        assertThat(fallback).contains("&#39;없는태그글&#39;에 대한 글이 없어요");
        assertThat(mockMvc.perform(get("/search?q=a")).andReturn().getResponse().getContentAsString()).contains("두 글자 이상 입력해 주세요");
        assertThat(mockMvc.perform(get("/search?q=개발자&tab=people")).andReturn().getResponse().getContentAsString())
                .contains("@kimdevlog").doesNotContain("kimdevgone");
        // 같은 방문자 1분 30번
        long me = member("srlimit");
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/search/posts?q=태그글").with(TestAuth.member(me))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/search/posts?q=태그글").with(TestAuth.member(me))).andExpect(status().isTooManyRequests());
        assertThat(Timestamp.from(T)).isNotNull();
    }
}
