package com.team.blog.account.integration;

import static com.team.blog.account.integration.ProfileTestSupport.patchProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.MemberSummaryQuery;
import com.team.blog.media.application.ImageStorage;
import com.team.blog.support.ImageFlow;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 003 T240: 프로필 이미지 연결·교체·기본으로(FR-014, FR-016, SC-003, SC-007). */
class ProfileImageAttachIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ImageStorage storage;

    @Autowired
    MemberSummaryQuery memberSummaryQuery;

    private long verified(String handle, String nickname) {
        return members.localMember(handle, nickname, handle + "@naver.com", "Blog#2026ok", true);
    }

    private Map<String, Object> member(long id) {
        return jdbc.queryForMap("SELECT nickname, bio, profile_image_id, profile_image_url FROM member WHERE id = ?", id);
    }

    private Map<String, Object> image(long id) {
        return jdbc.queryForMap("SELECT status, detached_at, storage_key FROM image WHERE id = ?", id);
    }

    private String url(long imageId) {
        return storage.publicUrl((String) image(imageId).get("storage_key"));
    }

    @Test
    void attachReplaceAndResetToDefault() throws Exception {
        long id = verified("kim755030", "김민서");
        ImageFlow flow = new ImageFlow(mockMvc);
        long first = flow.uploadProfile(id);

        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + first + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageId").value(first))
                .andExpect(jsonPath("$.profileImageUrl").value(url(first)));
        assertThat(member(id)).containsEntry("profile_image_id", first).containsEntry("profile_image_url", url(first));
        assertThat(image(first)).containsEntry("status", "ATTACHED").containsEntry("detached_at", null);

        // 같은 이미지를 다시 보내면 아무것도 바뀌지 않는다
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + first + "}")).andExpect(status().isOk());
        assertThat(image(first).get("detached_at")).isNull();

        long second = flow.uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + second + "}")).andExpect(status().isOk());
        assertThat(member(id)).containsEntry("profile_image_id", second);
        assertThat(image(second)).containsEntry("status", "ATTACHED").containsEntry("detached_at", null);
        assertThat(image(first).get("detached_at")).isNotNull();

        mockMvc.perform(patchProfile(id, "{\"profileImageId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageId").doesNotExist());
        assertThat(member(id)).containsEntry("profile_image_id", null).containsEntry("profile_image_url", null);
        assertThat(image(second).get("detached_at")).isNotNull();
    }

    @Test
    void invalidImagesAreRejectedAndNothingChanges() throws Exception {
        long id = verified("kim755030", "김민서");
        long other = verified("lee755030", "이서준");
        ImageFlow flow = new ImageFlow(mockMvc);
        long mine = flow.uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + mine + "}")).andExpect(status().isOk());

        long othersImage = flow.uploadProfile(other);
        long postImage = jdbc.queryForObject("""
                INSERT INTO image (uploader_id, storage_key, original_name, content_type, size_bytes, width, height, purpose)
                VALUES (?, 'images/2026/10/post.webp', 'post.webp', 'image/webp', 100, 256, 256, 'POST') RETURNING id""",
                Long.class, id);
        long notCompleted = flow.presign(id, "image/webp", 100).imageId();
        long detached = flow.uploadProfile(id);
        jdbc.update("UPDATE image SET detached_at = now() WHERE id = ?", detached);

        for (long candidate : List.of(othersImage, postImage, notCompleted, detached, 987_654L)) {
            mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + candidate + ",\"bio\":\"바뀌면 안 됨\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("profileImageId"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROFILE_IMAGE"))
                    .andExpect(jsonPath("$.errors[0].message").value("사용할 수 없는 이미지예요"));
            assertThat(member(id)).containsEntry("profile_image_id", mine).containsEntry("bio", null);
        }
        assertThat(image(othersImage)).containsEntry("status", "TEMP");
        assertThat(image(mine)).containsEntry("status", "ATTACHED").containsEntry("detached_at", null);
    }

    @Test
    void imageErrorIsReportedTogetherWithOtherFields() throws Exception {
        long id = verified("kim755030", "김민서");
        long other = verified("lee755030", "이서준");
        long othersImage = new ImageFlow(mockMvc).uploadProfile(other);
        mockMvc.perform(patchProfile(id, "{\"nickname\":\"김 민서\",\"bio\":\"오늘 시1발\",\"profileImageId\":" + othersImage + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
        assertThat(member(id)).containsEntry("nickname", "김민서").containsEntry("profile_image_id", null);
    }

    @Test
    void unverifiedMemberCanStillResetToDefault() throws Exception {
        long id = members.localMember("park755030", "박하늘", "park755030@naver.com", "Blog#2026ok", false);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":null,\"bio\":\"인증 전\"}")).andExpect(status().isOk());
        assertThat(member(id)).containsEntry("bio", "인증 전");
    }

    @Test
    void newImageShowsImmediatelyOnBlogHeaderAndAuthorDisplays() throws Exception {
        long id = verified("kim755030", "김민서");
        long imageId = new ImageFlow(mockMvc).uploadProfile(id);
        String html = mockMvc.perform(get("/@kim755030")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("avatar-default").doesNotContain(url(imageId));

        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + imageId + "}")).andExpect(status().isOk());
        html = mockMvc.perform(get("/@kim755030")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("src=\"" + url(imageId) + "\"").contains("alt=\"\"");
        assertThat(memberSummaryQuery.findByIds(List.of(id)).get(id).profileImageUrl()).isEqualTo(url(imageId));
    }

    @Test
    void attachedAtIsRecordedWithApplicationClock() throws Exception {
        long id = verified("kim755030", "김민서");
        ImageFlow flow = new ImageFlow(mockMvc);
        long first = flow.uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + first + "}")).andExpect(status().isOk());
        Instant now = clock.instant();
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":null}")).andExpect(status().isOk());
        java.sql.Timestamp detachedAt = (java.sql.Timestamp) image(first).get("detached_at");
        assertThat(detachedAt.toInstant()).isEqualTo(now);
    }
}
