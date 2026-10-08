package com.team.blog.teaser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 035 AI 티저(강성찬 개인 확장): 동의 필요, 제안만, 글쓴이가 저장한 한 줄이 카드 요약 자리에. */
class TeaserIT extends IntegrationTestBase {

    static volatile String geminiText = "\\\"트랜잭션이 왜 나를 배신했는지\\\" #jpa\\n둘째 줄";
    static final HttpServer SERVER;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVER.createContext("/v1beta/", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] body = ("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + geminiText + "\"}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        SERVER.start();
    }

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("blog.ai.tag-suggest.gemini.base-url", () -> base);
        registry.add("blog.ai.tag-suggest.gemini.api-key", () -> "test-gemini-key");
        registry.add("blog.ai.tag-suggest.ollama.base-url", () -> base);
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void suggestNeedsConsentAndSavedTeaserReplacesCardExcerpt() throws Exception {
        long me = members.localMember("teaserme", "teaserme", "teaserme@example.com", "Blog#2026ok", true);
        long other = members.localMember("teaserother", "teaserothe", "teaserother@example.com", "Blog#2026ok", true);
        long postId = posts.published(me, "트랜잭션 이야기", "본문 ".repeat(50), 1, Instant.parse("2026-10-09T00:00:00Z"));
        String url = "/@teaserme/posts/" + postId;
        // 동의 전: 화면에 동의 안내, 만들기 요청은 오류 안내
        assertThat(mockMvc.perform(get(url).with(TestAuth.member(me))).andReturn().getResponse().getContentAsString())
                .contains("카드 한 줄 소개").contains("먼저 동의해 주세요").doesNotContain("AI로 만들기</button>");
        mockMvc.perform(post("/posts/{id}/teaser/generate", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(flash().attribute("teaserError", "AI로 만들려면 먼저 동의해 주세요"));
        jdbc.update("INSERT INTO member_agreement (member_id, type) VALUES (?, 'AI')", me);
        // 제안: 첫 줄만, 따옴표·해시태그 제거, 저장하지 않음
        mockMvc.perform(post("/posts/{id}/teaser/generate", postId).with(csrf()).with(TestAuth.member(me)))
                .andExpect(flash().attribute("teaserDraft", "트랜잭션이 왜 나를 배신했는지"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_teaser", Integer.class)).isZero();
        // 저장 → 카드 요약 자리에(글자로만)
        mockMvc.perform(post("/posts/{id}/teaser", postId).param("teaser", "트랜잭션이 <b>배신</b>한 날").with(csrf())
                .with(TestAuth.member(me))).andExpect(status().is3xxRedirection());
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                .contains("트랜잭션이 &lt;b&gt;배신&lt;/b&gt;한 날");
        // 남의 글은 404, 비우면 지운다
        mockMvc.perform(post("/posts/{id}/teaser", postId).param("teaser", "남이 씀").with(csrf()).with(TestAuth.member(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/posts/{id}/teaser", postId).param("teaser", " ").with(csrf()).with(TestAuth.member(me)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_teaser", Integer.class)).isZero();
    }
}
