package com.team.blog.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.note.application.NotePurgeStep;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 033 짧은 기록(강성찬 개인 확장): 280자 글자만, 공개 범위, 본인만 쓰고 지움. */
class NoteIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotePurgeStep purge;

    private long member(String handle) {
        return members.localMember(handle, handle.substring(0, Math.min(10, handle.length())), handle + "@example.com", "Blog#2026ok", true);
    }

    @Test
    void writeShowAndDeleteWithVisibility() throws Exception {
        long me = member("noteme");
        long friend = member("notefriend");
        long stranger = member("notestranger");
        mockMvc.perform(post("/@notefriend/friend").param("action", "request").with(csrf()).with(TestAuth.member(me)));
        mockMvc.perform(post("/@noteme/friend").param("action", "accept").with(csrf()).with(TestAuth.member(friend)));
        for (String[] n : new String[][] {{"모두에게 <b>안녕</b>", "PUBLIC"}, {"친구에게만", "FRIENDS"}, {"나만 보는 메모", "PRIVATE"}}) {
            mockMvc.perform(post("/notes").param("content", n[0]).param("visibility", n[1]).param("back", "/@noteme")
                    .with(csrf()).with(TestAuth.member(me))).andExpect(status().is3xxRedirection());
        }
        String guest = mockMvc.perform(get("/@noteme/notes")).andReturn().getResponse().getContentAsString();
        assertThat(guest).contains("모두에게 &lt;b&gt;안녕&lt;/b&gt;").doesNotContain("친구에게만").doesNotContain("나만 보는 메모");
        assertThat(mockMvc.perform(get("/@noteme/notes").with(TestAuth.member(friend))).andReturn().getResponse()
                .getContentAsString()).contains("친구에게만").doesNotContain("나만 보는 메모");
        assertThat(mockMvc.perform(get("/@noteme").with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("짧은 기록").contains("나만 보는 메모").contains("기록하기");
        // 280자 넘음·빈 글은 400, 남의 기록은 못 지움
        mockMvc.perform(post("/notes").param("content", "가".repeat(281)).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/notes").param("content", "   ").with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest());
        long id = jdbc.queryForObject("SELECT min(id) FROM short_note WHERE author_id = ?", Long.class, me);
        mockMvc.perform(post("/notes/{id}/delete", id).with(csrf()).with(TestAuth.member(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(post("/notes/{id}/delete", id).with(csrf()).with(TestAuth.member(me))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM short_note WHERE author_id = ?", Integer.class, me)).isEqualTo(2);
        purge.purge(me);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM short_note WHERE author_id = ?", Integer.class, me)).isZero();
    }
}
