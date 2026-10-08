package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.publishBody;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 029 링크 공개(강성찬 개인 확장): 주소의 열쇠를 아는 사람만, 목록·검색·sitemap에는 없음, 새 링크면 예전 주소 막힘. */
class LinkShareIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private String keyFrom(String html) {
        Matcher m = Pattern.compile("\\?key=([A-Za-z0-9_-]+)").matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void onlyHoldersOfTheKeyCanReadAndItNeverLists() throws Exception {
        long me = writer(members, "linkwriter");
        long reader = writer(members, "linkreader");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(),
                publishBody("링크로만 보는 글", "비밀 본문", "[]", "LINK", 0)).with(TestAuth.member(me))).andExpect(status().isOk());
        String url = "/@linkwriter/posts/" + postId;
        String mine = mockMvc.perform(get(url).with(TestAuth.member(me))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(mine).contains("이 주소를 아는 사람만 볼 수 있어요");
        String key = keyFrom(mine);
        // 열쇠 없으면 404, 있으면 비회원도 읽음
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(TestAuth.member(reader))).andExpect(status().isNotFound());
        assertThat(mockMvc.perform(get(url).param("key", key)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).contains("링크로만 보는 글").contains("noindex");
        mockMvc.perform(get(url).param("key", "wrong")).andExpect(status().isNotFound());
        // 글 화면에서 부르는 API는 Referer의 열쇠로
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(csrf()).with(TestAuth.member(reader))
                .header("Referer", "http://localhost" + url + "?key=" + key)).andExpect(status().isOk());
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(csrf()).with(TestAuth.member(reader)))
                .andExpect(status().isNotFound());
        // 목록·검색·sitemap에는 없다
        for (String path : new String[] {"/", "/@linkwriter", "/api/posts", "/sitemap.xml", "/search?q=" + "링크로만"}) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString()).as(path)
                    .doesNotContain("링크로만 보는 글");
        }
        // 새 링크 → 예전 주소는 막힘(글쓴이만 만들 수 있음)
        mockMvc.perform(post("/posts/{id}/share-link", postId).with(csrf()).with(TestAuth.member(reader)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/posts/{id}/share-link", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get(url).param("key", key)).andExpect(status().isNotFound());
        String fresh = keyFrom(mockMvc.perform(get(url).with(TestAuth.member(me))).andReturn().getResponse().getContentAsString());
        assertThat(fresh).isNotEqualTo(key);
        mockMvc.perform(get(url).param("key", fresh)).andExpect(status().isOk());
    }
}
