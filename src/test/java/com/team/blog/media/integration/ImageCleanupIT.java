package com.team.blog.media.integration;

import static com.team.blog.account.integration.ProfileImageTestAccess.patchProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.media.application.ImageCleanupService;
import com.team.blog.media.application.ImageStorage;
import com.team.blog.support.ImageFlow;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 003 T241: 정리 작업 — TEMP 24시간, 연결이 끊긴 지 7일, 연결된 프로필 이미지는 지우지 않음(FR-015, FR-016, SC-004). */
class ImageCleanupIT extends IntegrationTestBase {

    @Autowired
    ImageCleanupService cleanup;

    @MockitoSpyBean
    ImageStorage storage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long verified() {
        return members.localMember("kim755030", "김민서", "kim755030@naver.com", "Blog#2026ok", true);
    }

    private boolean rowExists(long imageId) {
        return jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, imageId) == 1;
    }

    private String key(long imageId) {
        return jdbc.queryForObject("SELECT storage_key FROM image WHERE id = ?", String.class, imageId);
    }

    @Test
    void unsavedUploadIsDeletedAfter24Hours() throws Exception {
        long id = verified();
        long imageId = new ImageFlow(mockMvc).uploadProfile(id);
        String key = key(imageId);

        clock.advance(Duration.ofHours(23));
        cleanup.runOnce();
        assertThat(rowExists(imageId)).isTrue();
        assertThat(storage.inspect(key)).isPresent();

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        cleanup.runOnce();
        assertThat(rowExists(imageId)).isFalse();
        assertThat(storage.inspect(key)).isEmpty();
    }

    @Test
    void attachedProfileImageSurvivesAnyNumberOfRuns() throws Exception {
        long id = verified();
        long imageId = new ImageFlow(mockMvc).uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + imageId + "}")).andExpect(status().isOk());
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofDays(30));
            cleanup.runOnce();
        }
        assertThat(rowExists(imageId)).isTrue();
        assertThat(storage.inspect(key(imageId))).isPresent();
    }

    @Test
    void replacedImageIsDeletedSevenDaysAfterDetach() throws Exception {
        long id = verified();
        ImageFlow flow = new ImageFlow(mockMvc);
        long first = flow.uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + first + "}")).andExpect(status().isOk());
        long second = flow.uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + second + "}")).andExpect(status().isOk());
        String firstKey = key(first);

        clock.advance(Duration.ofDays(7).minusMinutes(1));
        cleanup.runOnce();
        assertThat(rowExists(first)).isTrue();

        clock.advance(Duration.ofMinutes(2));
        cleanup.runOnce();
        assertThat(rowExists(first)).isFalse();
        assertThat(storage.inspect(firstKey)).isEmpty();
        assertThat(rowExists(second)).isTrue();
    }

    @Test
    void imageReferencedByMemberIsSkippedEvenIfRowLooksDeletable() throws Exception {
        long id = verified();
        long imageId = new ImageFlow(mockMvc).uploadProfile(id);
        mockMvc.perform(patchProfile(id, "{\"profileImageId\":" + imageId + "}")).andExpect(status().isOk());
        jdbc.update("UPDATE image SET status = 'TEMP', detached_at = now() - interval '30 days' WHERE id = ?", imageId);
        clock.advance(Duration.ofDays(10));
        cleanup.runOnce();
        assertThat(rowExists(imageId)).isTrue();
        assertThat(storage.inspect(key(imageId))).isPresent();
    }

    @Test
    void storageDeleteFailureIsRetriedOnNextRun() throws Exception {
        long id = verified();
        long imageId = new ImageFlow(mockMvc).uploadProfile(id);
        String key = key(imageId);
        doThrow(new IllegalStateException("storage down")).when(storage).delete(eq(key));

        clock.advance(Duration.ofDays(2));
        cleanup.runOnce();
        assertThat(rowExists(imageId)).isFalse();
        assertThat(redis.opsForSet().isMember(ImageCleanupService.ORPHAN_KEYS, key)).isTrue();
        assertThat(storage.inspect(key)).isPresent();

        doCallRealMethod().when(storage).delete(eq(key));
        cleanup.runOnce();
        assertThat(redis.opsForSet().isMember(ImageCleanupService.ORPHAN_KEYS, key)).isFalse();
        assertThat(storage.inspect(key)).isEmpty();
    }
}
