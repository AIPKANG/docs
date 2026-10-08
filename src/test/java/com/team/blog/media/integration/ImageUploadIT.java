package com.team.blog.media.integration;

import static com.team.blog.support.ImageFlow.completeRequest;
import static com.team.blog.support.ImageFlow.presignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.support.ImageFlow;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.StorageTestClient;
import com.team.blog.support.TestImages;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 003 T239: 프로필 이미지 업로드(presign·complete) 권한·규격 검사(FR-012, FR-013, FR-017, SC-003). */
class ImageUploadIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ImageStorage storage;

    private long verified() {
        return members.localMember("kim755030", "김민서", "kim755030@naver.com", "Blog#2026ok", true);
    }

    private Map<String, Object> image(long id) {
        return jdbc.queryForMap("SELECT * FROM image WHERE id = ?", id);
    }

    private boolean imageExists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, id) == 1;
    }

    @Test
    void verifiedMemberUploadsProfileImageDirectlyToStorage() throws Exception {
        long id = verified();
        byte[] webp = TestImages.webp(256, 256);
        ImageFlow.Presigned presigned = new ImageFlow(mockMvc).presign(id, "image/webp", webp.length);
        Map<String, Object> row = image(presigned.imageId());
        assertThat(row).containsEntry("status", "TEMP").containsEntry("purpose", "PROFILE")
                .containsEntry("content_type", "image/webp").containsEntry("original_name", "내 사진.jpg");
        assertThat(row.get("width")).isNull();
        String key = (String) row.get("storage_key");
        assertThat(key).matches("images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp").doesNotContain("사진");
        assertThat(presigned.uploadUrl()).contains(key).contains("X-Amz-Signature");

        assertThat(StorageTestClient.put(presigned.uploadUrl(), presigned.headers(), webp)).isEqualTo(200);
        mockMvc.perform(completeRequest(id, presigned.imageId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").value(presigned.imageId()))
                .andExpect(jsonPath("$.url").value(storage.publicUrl(key)))
                .andExpect(jsonPath("$.width").value(256))
                .andExpect(jsonPath("$.height").value(256));
        assertThat(image(presigned.imageId())).containsEntry("width", 256).containsEntry("height", 256)
                .containsEntry("size_bytes", webp.length).containsEntry("status", "TEMP");
        assertThat(image(presigned.imageId()).get("thumb_storage_key")).isNull();

        // 같은 complete를 다시 보내도 같은 결과(멱등)
        mockMvc.perform(completeRequest(id, presigned.imageId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(storage.publicUrl(key)));
    }

    @Test
    void guestsUnverifiedAndWithdrawingMembersCannotUpload() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/images/presign")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"PROFILE\",\"contentType\":\"image/webp\",\"size\":100}"))
                .andExpect(status().isUnauthorized());
        long unverified = members.localMember("lee755030", "이서준", "lee755030@naver.com", "Blog#2026ok", false);
        mockMvc.perform(presignRequest(unverified, "image/webp", 100))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        long withdrawing = members.withdrawing("park755030", "박하늘", Instant.now());
        members.addLocalIdentity(withdrawing, "park755030@naver.com", "Blog#2026ok", Instant.now());
        mockMvc.perform(presignRequest(withdrawing, "image/webp", 100))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Integer.class)).isZero();
    }

    @Test
    void presignRejectsWrongTypeSizeAndPurpose() throws Exception {
        long id = verified();
        mockMvc.perform(presignRequest(id, "image/bmp", 100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID")).andExpect(jsonPath("$.detail").value("TYPE"));
        mockMvc.perform(presignRequest(id, "image/webp", 1048577))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("SIZE"));
        mockMvc.perform(presignRequest(id, "image/webp", 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("SIZE"));
        mockMvc.perform(presignRequest(id, "AVATAR", "image/webp", 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_PURPOSE_NOT_SUPPORTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Integer.class)).isZero();
    }

    @Test
    void twentyUploadsPerMinutePerMember() throws Exception {
        long id = verified();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(presignRequest(id, "image/webp", 100)).andExpect(status().isOk());
        }
        mockMvc.perform(presignRequest(id, "image/webp", 100))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void completeRejectsInvalidFilesAndDeletesThem() throws Exception {
        long id = verified();
        ImageFlow flow = new ImageFlow(mockMvc);

        assertRejected(id, flow, "image/webp", null, 100, "MISSING");
        assertRejected(id, flow, "image/png", TestImages.png(300, 300), -1, "DIMENSION");
        assertRejected(id, flow, "image/webp", TestImages.png(256, 256), -1, "CONTENT_MISMATCH");
        assertRejected(id, flow, "image/jpeg", TestImages.jpegWithExif(256, 256), -1, "METADATA");
        byte[] big = TestImages.webp(256, 256, 2000);
        assertRejected(id, flow, "image/webp", big, big.length - 100, "SIZE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Integer.class)).isZero();
    }

    private void assertRejected(long memberId, ImageFlow flow, String contentType, byte[] bytes, long declared,
                                String detail) throws Exception {
        long size = declared > 0 ? declared : bytes.length;
        ImageFlow.Presigned presigned = flow.presign(memberId, contentType, size);
        String key = (String) image(presigned.imageId()).get("storage_key");
        if (bytes != null) {
            assertThat(StorageTestClient.put(presigned.uploadUrl(), presigned.headers(), bytes)).isEqualTo(200);
        }
        mockMvc.perform(completeRequest(memberId, presigned.imageId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"))
                .andExpect(jsonPath("$.detail").value(detail));
        assertThat(imageExists(presigned.imageId())).isFalse();
        assertThat(storage.inspect(key)).isEmpty();
    }

    @Test
    void someoneElsesImageCannotBeCompleted() throws Exception {
        long owner = verified();
        long other = members.localMember("lee755030", "이서준", "lee755030@naver.com", "Blog#2026ok", true);
        ImageFlow.Presigned presigned = new ImageFlow(mockMvc).presign(owner, "image/webp", 100);
        mockMvc.perform(completeRequest(other, presigned.imageId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(completeRequest(owner, 999_999)).andExpect(status().isNotFound());
        assertThat(imageExists(presigned.imageId())).isTrue();
    }
}
