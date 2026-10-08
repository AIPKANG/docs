package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 006 T603: 공개 범위 × 보는 사람 × 상태(US1, FR-001~FR-008, SC). 볼 수 없으면 없는 글과 같은 404. */
class PostAccessMatrixIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private MockHttpServletResponse open(String handle, long postId, RequestPostProcessor viewer) throws Exception {
        MockHttpServletRequestBuilder request = get("/@" + handle + "/posts/" + postId);
        if (viewer != null) {
            request = request.with(viewer);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    /** 세션마다 다른 CSRF 토큰 값만 지우고 비교한다. */
    private static String normalized(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString().replaceAll("(name=\"_csrf\" content=\")[^\"]*", "$1")
                .replaceAll("(name=\"_csrf\" value=\")[^\"]*", "$1");
    }

    @Test
    void matrix() throws Exception {
        long author = writer(members, "matrixa");
        long other = writer(members, "matrixb");
        long publicPost = posts.published(author, "공개", "본문", 1, T);
        long privatePost = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", privatePost);
        long draftPublic = posts.draft(author, "임시 공개값", "본문", 0);
        long draftPrivate = posts.draft(author, "임시 비공개값", "본문", 0);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", draftPrivate);
        long trashed = posts.published(author, "휴지통", "본문", 1, T);
        posts.trash(trashed);

        RequestPostProcessor guest = null;
        RequestPostProcessor member = TestAuth.member(other);
        RequestPostProcessor admin = TestAuth.admin(other);
        RequestPostProcessor self = TestAuth.member(author);

        // 공개 글: 모두 200
        for (RequestPostProcessor viewer : new RequestPostProcessor[] {guest, member, admin, self}) {
            assertThat(open("matrixa", publicPost, viewer).getStatus()).isEqualTo(200);
        }
        // 비공개·임시글: 작성자만, 나머지는 없는 글과 같은 404 본문
        for (long hidden : new long[] {privatePost, draftPublic, draftPrivate}) {
            // 010: 작성자 본인의 임시글은 에디터로 302, 비공개 발행 글은 200
            assertThat(open("matrixa", hidden, self).getStatus()).isEqualTo(hidden == privatePost ? 200 : 302);
            for (RequestPostProcessor viewer : new RequestPostProcessor[] {guest, member, admin}) {
                MockHttpServletResponse response = open("matrixa", hidden, viewer);
                assertThat(response.getStatus()).isEqualTo(404);
                assertThat(normalized(response)).isEqualTo(normalized(open("matrixa", 999999, viewer)));
            }
        }
        // 휴지통: 작성자도 404
        for (RequestPostProcessor viewer : new RequestPostProcessor[] {guest, member, admin, self}) {
            assertThat(open("matrixa", trashed, viewer).getStatus()).isEqualTo(404);
        }
        // 탈퇴 유예 작성자의 공개 글: 아무도 못 봄
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", author);
        assertThat(open("matrixa", publicPost, guest).getStatus()).isEqualTo(404);
    }

    @Test
    void notFoundPageHasSharedPreviewNoindexAndNoStore() throws Exception {
        long author = writer(members, "previewa");
        long privatePost = posts.published(author, "비밀 제목", "비밀 본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", privatePost);
        MockHttpServletResponse hidden = open("previewa", privatePost, null);
        MockHttpServletResponse missing = open("previewa", 999999, null);
        for (MockHttpServletResponse response : new MockHttpServletResponse[] {hidden, missing}) {
            String html = response.getContentAsString();
            assertThat(html).contains("<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">")
                    .contains("친구 공개·비공개 글이거나 삭제된 글입니다.").contains("<meta name=\"robots\" content=\"noindex\">")
                    .doesNotContain("비밀 제목");
            assertThat(response.getHeader("Cache-Control")).contains("no-store");
        }
        assertThat(normalized(hidden)).isEqualTo(normalized(missing));
        assertThat(open("previewa", privatePost, TestAuth.member(author)).getHeader("Cache-Control")).contains("no-store");
    }
}
