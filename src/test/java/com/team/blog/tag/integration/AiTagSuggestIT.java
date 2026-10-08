package com.team.blog.tag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** 021 AI 태그 추천: 가짜 Gemini·Ollama HTTP 서버로 전환·캐시·한도를 검사한다. */
class AiTagSuggestIT extends IntegrationTestBase {

    record Reply(int status, String body) {
    }

    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final List<String> geminiKeys = new CopyOnWriteArrayList<>();
    static volatile Reply gemini = new Reply(200, "");
    static volatile Reply ollama = new Reply(200, "");
    static final HttpServer SERVER;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVER.createContext("/v1beta/", ex -> {
            calls.add("gemini " + ex.getRequestURI().getRawQuery());
            geminiKeys.add(String.valueOf(ex.getRequestHeaders().getFirst("x-goog-api-key")));
            ex.getRequestBody().readAllBytes();
            respond(ex, gemini);
        });
        SERVER.createContext("/api/generate", ex -> {
            calls.add("ollama");
            ex.getRequestBody().readAllBytes();
            respond(ex, ollama);
        });
        SERVER.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, Reply reply) throws IOException {
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(reply.status(), bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("blog.ai.tag-suggest.gemini.base-url", () -> base);
        registry.add("blog.ai.tag-suggest.gemini.api-key", () -> "test-gemini-key");
        registry.add("blog.ai.tag-suggest.gemini.daily-limit", () -> "5");
        registry.add("blog.ai.tag-suggest.ollama.base-url", () -> base);
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    static String geminiTags(String json) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(json) + "}]}}]}";
    }

    static String ollamaTags(String json) {
        return "{\"response\":" + quote(json) + ",\"done\":true}";
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static final String BODY = "스프링 부트에서 JPA 트랜잭션을 다루는 방법을 정리했다. ".repeat(6);

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    long author;
    long postId;

    @BeforeEach
    void setUp() {
        calls.clear();
        geminiKeys.clear();
        gemini = new Reply(200, geminiTags("{\"tags\":[\"JPA\",\"spring boot\",\"transaction\",\"<b>bad</b>\",\"existing\"]}"));
        ollama = new Reply(200, ollamaTags("{\"tags\":[\"jpa\",\"local-tag\"]}"));
        author = members.localMember("aiauthor", "aiauthor", "aiauthor@example.com", "Blog#2026ok", true);
        postId = posts.draft(author, "트랜잭션 정리", BODY, 1);
    }

    private ResultActions ask(String content, boolean refresh) throws Exception {
        return mockMvc.perform(post("/api/posts/{id}/tag-suggestions", postId).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"트랜잭션 정리\",\"contentMd\":" + quote(content) + ",\"currentTags\":[\"existing\"],"
                        + "\"visibility\":\"PUBLIC\",\"refresh\":" + refresh + "}"));
    }

    private void consent() throws Exception {
        mockMvc.perform(post("/api/me/ai-consent").with(csrf()).with(TestAuth.member(author))).andExpect(status().isNoContent());
    }

    @Test
    void checksOrderAndConsentBeforeSendingAnything() throws Exception {
        long other = members.localMember("aiother", "aiother", "aiother@example.com", "Blog#2026ok", true);
        long unverified = members.localMember("aiunver", "aiunver", "aiunver@example.com", "Blog#2026ok", false);
        String body = "{\"title\":\"t\",\"contentMd\":" + quote(BODY) + ",\"visibility\":\"PUBLIC\"}";
        mockMvc.perform(post("/api/posts/{id}/tag-suggestions", postId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts/{id}/tag-suggestions", postId).with(csrf()).with(TestAuth.member(unverified))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/posts/{id}/tag-suggestions", postId).with(csrf()).with(TestAuth.member(other))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/{id}/tag-suggestions", postId).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("PUBLIC", "PRIVATE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AI_PUBLIC_ONLY"));
        ask(BODY, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_CONSENT_REQUIRED"));
        assertThat(calls).isEmpty();
        consent();
        ask("짧은 글", false).andExpect(status().is(422)).andExpect(jsonPath("$.message").value("글을 조금 더 쓴 뒤 추천받아 보세요"));
        assertThat(calls).isEmpty();
        // 발행된 비공개 글은 화면 값이 공개여도 거절
        long priv = posts.published(author, "비공개", BODY, 1, Instant.now());
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        mockMvc.perform(post("/api/posts/{id}/tag-suggestions", priv).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        // 철회하면 다시 물음
        mockMvc.perform(delete("/api/me/ai-consent").with(csrf()).with(TestAuth.member(author))).andExpect(status().isNoContent());
        ask(BODY, false).andExpect(status().isConflict());
    }

    @Test
    void geminiSuggestsFiltersAndCachesTwoWays() throws Exception {
        consent();
        ask(BODY, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").value(org.hamcrest.Matchers.contains("jpa", "spring-boot", "transaction")))
                .andExpect(jsonPath("$.provider").value("gemini")).andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.remainingToday").value(19)).andExpect(jsonPath("$.truncated").value(false));
        assertThat(calls).containsExactly("gemini null");
        assertThat(geminiKeys).containsExactly("test-gemini-key");
        // ① 완전히 같은 내용(공백만 다름): 다시 부르지 않고 횟수도 그대로
        ask(BODY.replace(" ", "  "), false).andExpect(jsonPath("$.cached").value(true)).andExpect(jsonPath("$.remainingToday").value(19));
        // ② 오타 하나: 비슷한 내용 재사용
        ask(BODY.replaceFirst("정리했다", "정리햇다"), false).andExpect(jsonPath("$.cached").value(true));
        assertThat(calls).hasSize(1);
        // [다시 추천]은 ②를 건너뛴다
        ask(BODY.replaceFirst("정리했다", "정리햇다"), true).andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.remainingToday").value(18));
        assertThat(calls).hasSize(2);
        // 키에 내용이 드러나지 않음
        assertThat(redis.keys("ai:tag:*")).allMatch(k -> !k.contains("트랜잭션"));
        // 긴 글은 잘렸음을 알림
        ask("가나다라마바사 ".repeat(1200), false).andExpect(jsonPath("$.truncated").value(true));
        // 검증에서 모두 걸러지면 저장하지 않고 안내
        gemini = new Reply(200, geminiTags("{\"tags\":[\"existing\",\"<x>\"]}"));
        ask("완전히 새로운 내용으로 바꾼 글 ".repeat(10), false).andExpect(jsonPath("$.tags.length()").value(0))
                .andExpect(jsonPath("$.message").value("추천할 태그를 찾지 못했어요"));
        ask("완전히 새로운 내용으로 바꾼 글 ".repeat(10), false).andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void switchesToOllamaOnLimitsAndFailsClearly() throws Exception {
        consent();
        // 하루 한도 429 → 같은 요청을 자체 AI로, 이후에도 자체 AI
        gemini = new Reply(429, "{\"error\":{\"code\":429,\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}");
        ask(BODY, false).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.tags").value(org.hamcrest.Matchers.contains("jpa", "local-tag")));
        assertThat(calls).containsExactly("gemini null", "ollama");
        assertThat(redis.hasKey("ai:gemini:exhausted")).isTrue();
        assertThat(redis.getExpire("ai:gemini:exhausted")).isPositive();
        ask(BODY + " 추가 문장 하나 더 붙임 그리고 더 길게 써서 다르게", true).andExpect(jsonPath("$.provider").value("ollama"));
        assertThat(calls).containsExactly("gemini null", "ollama", "ollama");
        // 시간 초과·서버 오류 → 이 요청은 실패, 60초 쉬는 동안은 자체 AI
        redis.delete("ai:gemini:exhausted");
        gemini = new Reply(500, "{}");
        ask("새 글 내용 ".repeat(30), false).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        assertThat(redis.getExpire("ai:gemini:cooldown")).isBetween(1L, 60L);
        ask("또 다른 글 내용 ".repeat(30), false).andExpect(jsonPath("$.provider").value("ollama"));
        // 우리가 센 오늘 호출 수가 한도 이상이면 429 전에 미리 전환
        redis.delete("ai:gemini:cooldown");
        gemini = new Reply(200, geminiTags("{\"tags\":[\"jpa\"]}"));
        String day = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Los_Angeles")).toString();
        redis.opsForValue().set("ai:gemini:count:" + day, "5");
        calls.clear();
        ask("세 번째 다른 글 ".repeat(30), false).andExpect(jsonPath("$.provider").value("ollama"));
        assertThat(calls).containsExactly("ollama");
        assertThat(redis.hasKey("ai:gemini:exhausted")).isTrue();
        // 자체 AI 동시 처리 초과 → 잠시 후
        redis.opsForValue().set("ai:ollama:inflight", "1");
        ask("네 번째 다른 글 ".repeat(30), false).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요"));
        redis.delete("ai:ollama:inflight");
        // 형식이 틀리면 실패(저장 안 함)
        ollama = new Reply(200, ollamaTags("{\"tags\":\"jpa\"}"));
        ask("다섯 번째 다른 글 ".repeat(30), false).andExpect(status().isServiceUnavailable());
        // 둘 다 실패해도 자동 저장은 그대로 된다
        ollama = new Reply(500, "{}");
        ask("여섯 번째 다른 글 ".repeat(30), false).andExpect(status().isServiceUnavailable());
        mockMvc.perform(put("/api/posts/{id}/autosave", postId).with(csrf()).with(TestAuth.member(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"contentMd\":\"c\",\"baseVersion\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void perUserDailyLimitCountsOnlyRealCalls() throws Exception {
        consent();
        String key = "ai:tag:user:" + author + ":" + LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
        redis.opsForValue().set(key, "19");
        ask(BODY, false).andExpect(status().isOk()).andExpect(jsonPath("$.remainingToday").value(0));
        // 재사용은 횟수와 무관
        ask(BODY, false).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        ask("전혀 다른 새 글 ".repeat(30), false).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT"));
    }
}
