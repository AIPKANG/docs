package com.team.blog.tag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriUtils;

/** 013: 태그별 목록·전체 태그·자동완성·블로그 태그 필터(FR-013~FR-027). */
class TagPagesIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long writer(String handle) {
        return members.localMember(handle, handle, handle + "@example.com", "Blog#2026ok", true);
    }

    private long tagged(long author, String title, Instant at, String... tags) {
        long id = posts.published(author, title, "본문", 1, at);
        for (int i = 0; i < tags.length; i++) {
            jdbc.update("INSERT INTO tag (name) VALUES (?) ON CONFLICT DO NOTHING", tags[i]);
            jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) SELECT ?, id, ? FROM tag WHERE name = ?", id, i, tags[i]);
        }
        return id;
    }

    private static String path(String name) {
        return "/tags/" + UriUtils.encodePathSegment(name, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void tagPageShowsOnlyPublicPostsAndCount() throws Exception {
        long a = writer("tagauthor");
        long b = writer("tagleaver");
        tagged(a, "공개 jpa", T, "jpa");
        long priv = tagged(a, "비공개 jpa", T.plusSeconds(1), "jpa");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long trashed = tagged(a, "휴지통 jpa", T.plusSeconds(2), "jpa");
        posts.trash(trashed);
        tagged(b, "탈퇴 jpa", T.plusSeconds(3), "jpa");
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", b);
        String html = mockMvc.perform(get("/tags/jpa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("#jpa").contains("공개 글 1").contains("공개 jpa")
                .doesNotContain("비공개 jpa").doesNotContain("휴지통 jpa").doesNotContain("탈퇴 jpa");
        mockMvc.perform(get("/api/tags/jpa/posts")).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void addressesRoundTripRedirectAndNotFound() throws Exception {
        long a = writer("tagaddr");
        for (String name : new String[] {"c#", "c++", "node.js", ".net", "스프링-부트", "자바_기초"}) {
            tagged(a, "글 " + name, T, name);
            String html = mockMvc.perform(get(URI.create(path(name)))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as(name).contains("글 " + name).contains("공개 글 1");
        }
        assertThat(path("c#")).isEqualTo("/tags/c%23");
        mockMvc.perform(get(URI.create("/tags/Spring%20Boot"))).andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/tags/spring-boot"));
        mockMvc.perform(get(URI.create("/tags/%23JPA"))).andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/tags/jpa"));
        mockMvc.perform(get(URI.create("/tags/%F0%9F%94%A5"))).andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create("/tags/---"))).andExpect(status().isNotFound());
        // 형식은 맞지만 공개 글이 없는 태그: 아무도 안 씀 = 비공개 글에만 쓰임
        long priv = tagged(a, "비밀", T, "secret-only");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        String unused = mockMvc.perform(get("/tags/never-used")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String privOnly = mockMvc.perform(get("/tags/secret-only")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(unused).contains("아직 이 태그로 공개된 글이 없어요").contains("공개 글 0");
        assertThat(privOnly.replace("secret-only", "X").replaceAll("content=\"[^\"]*\"", ""))
                .isEqualTo(unused.replace("never-used", "X").replaceAll("content=\"[^\"]*\"", ""));
    }

    @Test
    void allTagsTop100SortedAndCached() throws Exception {
        long a = writer("tagtop");
        tagged(a, "1", T, "java", "spring");
        tagged(a, "2", T, "java", "spring");
        tagged(a, "3", T, "java", "alpha");
        long priv = tagged(a, "4", T, "hidden-tag");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        mockMvc.perform(get("/api/tags"))
                .andExpect(jsonPath("$[0].name").value("java")).andExpect(jsonPath("$[0].postCount").value(3))
                .andExpect(jsonPath("$[1].name").value("spring"))
                .andExpect(jsonPath("$[2].name").value("alpha"))
                .andExpect(jsonPath("$.length()").value(3));
        assertThat(redis.hasKey("tags:top")).isTrue();
        assertThat(redis.getExpire("tags:top")).isBetween(1L, 600L);
        assertThat(mockMvc.perform(get("/tags")).andReturn().getResponse().getContentAsString())
                .contains("#java 3").doesNotContain("hidden-tag");
    }

    @Test
    void suggestShowsMineAndPublicOnly() throws Exception {
        long me = writer("tagme");
        long other = writer("tagother");
        long myPrivate = tagged(me, "내 비공개", T, "spring-mine");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", myPrivate);
        tagged(other, "공개", T, "spring-boot");
        tagged(other, "공개2", T, "spring-boot");
        long theirPrivate = tagged(other, "남의 비공개", T, "spring-secret");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", theirPrivate);
        mockMvc.perform(get("/api/tags/suggest").param("q", "#SPR").with(TestAuth.member(me)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("spring-mine")).andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[1].name").value("spring-boot")).andExpect(jsonPath("$[1].postCount").value(2));
        mockMvc.perform(get("/api/tags/suggest").param("q", "###").with(TestAuth.member(me))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/tags/suggest").param("q", "s")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tags/suggest").param("q", "50%").with(TestAuth.member(me))).andExpect(status().isOk());
    }

    @Test
    void blogTagRowAndFilter() throws Exception {
        long a = writer("tagblog");
        for (int i = 0; i < 12; i++) {
            tagged(a, "글 " + i, T.plus(Duration.ofMinutes(i)), "t" + i, "common");
        }
        tagged(a, "jpa 글", T, "jpa");
        String html = mockMvc.perform(get("/@tagblog")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("#common 12").contains("태그 더 보기");
        String filtered = mockMvc.perform(get("/@tagblog").param("tag", "jpa")).andReturn().getResponse().getContentAsString();
        assertThat(filtered).contains("jpa 글").doesNotContain("글 3<").contains("필터 해제");
        mockMvc.perform(get("/@tagblog").param("tag", "JPA")).andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/@tagblog?tag=jpa"));
        mockMvc.perform(get("/api/members/tagblog/tags")).andExpect(jsonPath("$[0].name").value("common"));
        mockMvc.perform(get("/api/members/tagblog/posts").param("tag", "common"))
                .andExpect(jsonPath("$.items.length()").value(9)).andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void concurrentPublishWithSameNewTagCreatesOneTagAndErrorsCarryValue() throws Exception {
        long a = writer("tagrace");
        List<Long> drafts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            drafts.add(posts.draft(a, "", "", 0));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (long id : drafts) {
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/posts/{id}/publish", id).with(csrf()).with(TestAuth.member(a))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"contentMd\":\"c\",\"tags\":[\"brand-new\"],\"visibility\":\"PUBLIC\",\"baseVersion\":0}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag WHERE name = 'brand-new'", Integer.class)).isEqualTo(1);

        long bad = posts.draft(a, "", "", 0);
        mockMvc.perform(post("/api/posts/{id}/publish", bad).with(csrf()).with(TestAuth.member(a))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"contentMd\":\"c\",\"tags\":[\"ok\",\"🔥hot\"],\"visibility\":\"PUBLIC\",\"baseVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tags[1]"))
                .andExpect(jsonPath("$.errors[0].value").value("🔥hot"));
    }
}
