package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.autosave;
import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.application.PostListQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 011 T1106: 삭제·복구·영구 삭제(FR-016~FR-032). */
class PostTrashIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PostListQuery listQuery;

    private Map<String, Object> row(long id) {
        return posts.post(id);
    }

    @Test
    void trashHidesEverywhereKeepsDataAndRestoreReturnsToSamePlace() throws Exception {
        long me = writer(members, "trasher");
        long older = posts.published(me, "먼저 글", "본문", 1, T);
        long id = posts.published(me, "지울 글", "본문", 1, T.plusSeconds(60));
        long newer = posts.published(me, "나중 글", "본문", 1, T.plusSeconds(120));
        jdbc.update("UPDATE post SET like_count = 5, comment_count = 3 WHERE id = ?", id);
        jdbc.update("INSERT INTO tag (name) VALUES ('keep')");
        jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) SELECT ?, id, 0 FROM tag", id);
        Map<String, Object> before = row(id);

        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trashed").value(true)).andExpect(jsonPath("$.purgeAt").exists());
        assertThat(row(id)).containsEntry("status", "PUBLISHED").containsEntry("visibility", "PUBLIC");
        assertThat(row(id).get("deleted_at")).isNotNull();
        mockMvc.perform(get("/@trasher/posts/{id}", id).with(TestAuth.member(me))).andExpect(status().isNotFound());
        assertThat(listQuery.feed(null).items()).extracting("id").containsExactly(newer, older);
        assertThat(listQuery.publicCount(me)).isEqualTo(2);
        // 휴지통 글은 저장·자동 저장·발행·공개 범위 변경 모두 404
        mockMvc.perform(autosave(id, "x", "y", 1).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(manualSave(id, "x", "y", 1).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(publish(id, "x", "y", 1).with(TestAuth.member(me))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/posts/{id}/visibility", id).with(csrf()).with(TestAuth.member(me))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}")).andExpect(status().isNotFound());
        // 다시 삭제해도 그대로 성공
        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf()).with(TestAuth.member(me))).andExpect(status().isOk());

        mockMvc.perform(post("/api/posts/{id}/restore", id).with(csrf()).with(TestAuth.member(me))).andExpect(status().isOk());
        Map<String, Object> after = row(id);
        for (String kept : new String[] {"first_public_at", "status", "visibility", "like_count", "comment_count", "edit_version"}) {
            assertThat(after.get(kept)).as(kept).isEqualTo(before.get(kept));
        }
        assertThat(after.get("deleted_at")).isNull();
        assertThat(listQuery.feed(null).items()).extracting("id").containsExactly(newer, id, older);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_tag WHERE post_id = ?", Integer.class, id)).isEqualTo(1);
        mockMvc.perform(post("/api/posts/{id}/restore", id).with(csrf()).with(TestAuth.member(me))).andExpect(status().isNotFound());
    }

    @Test
    void emptyDraftIsPurgedImmediatelyAndAutosaveIsFlushedBeforeTrash() throws Exception {
        long me = writer(members, "emptydel");
        long empty = posts.draft(me, " ", "\n", 0);
        mockMvc.perform(delete("/api/posts/{id}", empty).with(csrf()).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.purged").value(true));
        assertThat(posts.exists(empty)).isFalse();

        long draft = posts.draft(me, "", "", 0);
        mockMvc.perform(autosave(draft, "버퍼 제목", "버퍼 본문", 0).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/posts/{id}", draft).with(csrf()).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.trashed").value(true));
        assertThat(row(draft)).containsEntry("title", "버퍼 제목").containsEntry("content_md", "버퍼 본문");
        assertThat(posts.buffer(draft)).isEmpty();
    }

    @Test
    void permanentDeleteCascadesButKeepsTagAndDetachesPhotos() throws Exception {
        long me = writer(members, "purger");
        long other = writer(members, "purgeroth");
        long id = posts.published(me, "영구 삭제", "본문", 1, T);
        jdbc.update("INSERT INTO tag (name) VALUES ('keepme')");
        jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) SELECT ?, id, 0 FROM tag", id);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", id, other);
        jdbc.update("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '댓글')", id, other);
        long image = jdbc.queryForObject("""
                INSERT INTO image (uploader_id, storage_key, original_name, content_type, size_bytes, status, purpose)
                VALUES (?, 'images/x/1.webp', 'a', 'image/webp', 10, 'ATTACHED', 'POST') RETURNING id
                """, Long.class, me);
        jdbc.update("INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", id, image);

        mockMvc.perform(delete("/api/posts/{id}/permanent", id).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNotFound()); // 휴지통에 없음
        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf()).with(TestAuth.member(me))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/posts/{id}/permanent", id).with(csrf()).with(TestAuth.member(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/{id}/permanent", id).with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isNoContent());
        assertThat(posts.exists(id)).isFalse();
        for (String table : new String[] {"post_tag", "post_like", "comment", "post_image"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE post_id = ?", Integer.class, id)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag WHERE name = 'keepme'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detached_at FROM image WHERE id = ?", java.sql.Timestamp.class, image)).isNotNull();
        long next = posts.draft(me, "새 글", "", 0);
        assertThat(next).isGreaterThan(id);
    }

    @Test
    void permissionsAndNoScriptForms() throws Exception {
        long me = writer(members, "trashperm");
        long other = writer(members, "trashprmx");
        long id = posts.published(me, "글", "본문", 1, T);
        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf()).with(TestAuth.member(other))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf()).with(TestAuth.admin(other))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/{id}", id).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(row(id).get("deleted_at")).isNull();
        long unverified = members.localMember("trashunv", "미인증", "trashunv@example.com", "Blog#2026ok", false);
        long hisDraft = posts.draft(unverified, "인증 전 글", "본문", 0);
        mockMvc.perform(delete("/api/posts/{id}", hisDraft).with(csrf()).with(TestAuth.member(unverified))).andExpect(status().isOk());

        mockMvc.perform(post("/manage/posts/{id}/trash", id).param("tab", "published").with(csrf()).with(TestAuth.member(me)))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/manage/posts?tab=published"));
        mockMvc.perform(post("/manage/posts/{id}/restore", id).param("tab", "trash").with(csrf()).with(TestAuth.member(me)))
                .andExpect(redirectedUrl("/manage/posts?tab=trash"));
        assertThat(row(id).get("deleted_at")).isNull();
    }

    @Test
    void concurrentTrashRestorePurgeStayConsistent() throws Exception {
        long me = writer(members, "trashrace");
        long id = posts.published(me, "경합", "본문", 1, T);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                var request = switch (n % 3) {
                    case 0 -> delete("/api/posts/{id}", id);
                    case 1 -> post("/api/posts/{id}/restore", id);
                    default -> delete("/api/posts/{id}/permanent", id);
                };
                return mockMvc.perform(request.with(csrf()).with(TestAuth.member(me))).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isIn(200, 204, 404);
        }
        pool.shutdown();
        if (posts.exists(id)) {
            assertThat(row(id).get("status")).isEqualTo("PUBLISHED");
        }
    }
}
