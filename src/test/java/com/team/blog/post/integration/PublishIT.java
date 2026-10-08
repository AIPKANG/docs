package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.publish;
import static com.team.blog.post.integration.PostTestSupport.publishBody;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 005 T507: 첫 발행(US1, FR-003~FR-013, SC-001, SC-006, SC-008). */
class PublishIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private List<String> tagsOf(long postId) {
        return jdbc.queryForList("SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = ? ORDER BY pt.position",
                String.class, postId);
    }

    @Test
    void firstPublishMakesPostReadableByAnyone() throws Exception {
        clock.set(NOW);
        long me = writer(members, "publisher");
        long postId = posts.draft(me, "", "", 3);
        String body = publishBody("  JPA N+1 정리​ ", "## 문제\n\n지연 로딩 `코드`\n\n```\n빼기\n```",
                "[\"Spring Boot\", \"#JPA\", \"spring boot\"]", "PUBLIC", 3);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), body).with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/@publisher/posts/" + postId))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-07T05:00:00Z"))
                .andExpect(jsonPath("$.firstPublicAt").value("2026-10-07T05:00:00Z"))
                .andExpect(jsonPath("$.editedAt").doesNotExist());

        Map<String, Object> row = posts.post(postId);
        assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("title", "JPA N+1 정리")
                .containsEntry("edit_version", 4L).containsEntry("excerpt", "문제 지연 로딩 코드");
        assertThat((String) row.get("content_html")).contains("<h3 id=\"h-문제\">문제</h3>");
        assertThat(((Timestamp) row.get("published_at")).toInstant()).isEqualTo(NOW);
        assertThat(row.get("edited_at")).isNull();
        assertThat(tagsOf(postId)).containsExactly("spring-boot", "jpa");

        String html = mockMvc.perform(get("/@publisher/posts/{id}", postId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("JPA N+1 정리").contains("#spring-boot").doesNotContain("수정됨")
                .doesNotContain(">수정</a>");
    }

    @Test
    void everyFailingFieldIsReportedAndDraftStays() throws Exception {
        long me = writer(members, "badinput");
        long postId = posts.draft(me, "", "", 0);
        String body = publishBody("   ​", " \n ", "[\"ok\",\"🔥hot\",\"" + "a".repeat(31) + "\"]", "PROTECTED", 0); // FRIENDS는 025에서 허용
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), body).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("TITLE_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("제목을 입력해 주세요."))
                .andExpect(jsonPath("$.errors[1].code").value("CONTENT_REQUIRED"))
                .andExpect(jsonPath("$.errors[2].field").value("tags[1]"))
                .andExpect(jsonPath("$.errors[2].code").value("INVALID_TAG"))
                .andExpect(jsonPath("$.errors[3].field").value("tags[2]"))
                .andExpect(jsonPath("$.errors[3].code").value("TAG_TOO_LONG"))
                .andExpect(jsonPath("$.errors[4].code").value("INVALID_VISIBILITY"));
        assertThat(posts.post(postId)).containsEntry("status", "DRAFT").containsEntry("edit_version", 0L);
    }

    @Test
    void lengthsTagCountAndPendingImagesAreRejected() throws Exception {
        long me = writer(members, "limits");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(),
                        publishBody("가".repeat(101), "a".repeat(100_001), "[]", "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("TITLE_TOO_LONG"))
                .andExpect(jsonPath("$.errors[1].code").value("CONTENT_TOO_LONG"));
        StringBuilder eleven = new StringBuilder("[");
        for (int i = 0; i < 11; i++) {
            eleven.append(i == 0 ? "" : ",").append("\"t").append(i).append('"');
        }
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(),
                        publishBody("제목", "본문", eleven.append("]").toString(), "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_MANY_TAGS"));
        // 대소문자·공백만 다른 태그는 하나로 합쳐 개수를 센다
        StringBuilder dup = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            dup.append("\"t").append(i).append("\",\" T").append(i).append(" \",");
        }
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(),
                        publishBody("사진 대기", "![업로드 대기](local:7f3e)", "[]", "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(jsonPath("$.errors[0].code").value("PENDING_IMAGES"));
        assertThat(posts.post(postId)).containsEntry("status", "DRAFT");
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(),
                        publishBody("합치기", "본문", dup.append("\"t0\"]").toString(), "PUBLIC", 0)).with(TestAuth.member(me)))
                .andExpect(status().isOk());
        assertThat(tagsOf(postId)).hasSize(10);
    }

    @Test
    void scriptsInBodyAndTitleNeverExecute() throws Exception {
        long me = writer(members, "xsspub");
        long postId = posts.draft(me, "", "", 0);
        String body = publishBody("<script>alert(1)</script>", "<img src=x onerror=alert(1)> [x](javascript:alert(1))\n\n<svg onload=alert(1)>",
                "[]", "PUBLIC", 0);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), body).with(TestAuth.member(me))).andExpect(status().isOk());
        String html = mockMvc.perform(get("/@xsspub/posts/{id}", postId)).andReturn().getResponse().getContentAsString();
        String article = html.substring(html.indexOf("<article"), html.indexOf("</article>"));
        assertThat(article).doesNotContain("<script").doesNotContain("<svg").doesNotContain("<img")
                .doesNotContain("href=\"javascript").contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void privatePublishHasNoFirstPublicAt() throws Exception {
        clock.set(NOW);
        long me = writer(members, "privpub");
        long postId = posts.draft(me, "", "", 0);
        mockMvc.perform(publish(postId, UUID.randomUUID().toString(), publishBody("비공개", "본문", "[]", "PRIVATE", 0))
                        .with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstPublicAt").doesNotExist());
        mockMvc.perform(get("/@privpub/posts/{id}", postId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/@privpub/posts/{id}", postId).with(TestAuth.member(me))).andExpect(status().isOk());
    }
}
