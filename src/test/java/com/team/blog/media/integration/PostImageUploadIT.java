package com.team.blog.media.integration;

import static com.team.blog.support.ImageFlow.completeRequest;
import static com.team.blog.support.ImageFlow.postPresignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.support.ImageFlow;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.StorageTestClient;
import com.team.blog.support.TestImages;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 008 T805: 글 사진 업로드와 서버 재검사(US1·US2, FR-001~FR-009, FR-031). */
class PostImageUploadIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ImageStorage storage;

    private long verified(String handle) {
        return members.localMember(handle, handle, handle + "@example.com", "Blog#2026ok", true);
    }

    private String key(long imageId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM image WHERE id = ?", String.class, imageId);
    }

    private boolean rowExists(long imageId) {
        return jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Integer.class, imageId) == 1;
    }

    @Test
    void uploadsOriginalAndThumbnailDirectlyToStorage() throws Exception {
        long me = verified("uploader");
        ImageFlow.Uploaded up = new ImageFlow(mockMvc).uploadPostWebp(me);
        String original = key(up.imageId(), "storage_key");
        String thumb = key(up.imageId(), "thumb_storage_key");
        assertThat(original).matches("images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp");
        assertThat(thumb).isEqualTo(original.replace(".webp", "_thumb.webp"));
        assertThat(up.url()).endsWith(original).doesNotContain("휴가");
        assertThat(up.thumbUrl()).endsWith(thumb);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM image WHERE id = ?", up.imageId());
        assertThat(row).containsEntry("purpose", "POST").containsEntry("status", "TEMP").containsEntry("width", 1200)
                .containsEntry("height", 800).containsEntry("original_name", "휴가 사진.jpg");
        assertThat((Integer) row.get("thumb_size_bytes")).isPositive();
        assertThat(StorageTestClient.get(up.url())).isEqualTo(200);
        assertThat(StorageTestClient.getHeader(up.url(), "Cache-Control")).isEqualTo(ImageStorage.CACHE_CONTROL);
        assertThat(StorageTestClient.getHeader(up.thumbUrl(), "Cache-Control")).isEqualTo(ImageStorage.CACHE_CONTROL);
    }

    @Test
    void thumbnailIsOptionalAndGifWithinLimitsPasses() throws Exception {
        long me = verified("gifuser");
        ImageFlow.Uploaded noThumb = new ImageFlow(mockMvc).uploadPost(me, "image/png", TestImages.png(300, 200), null);
        assertThat(noThumb.thumbUrl()).isNull();
        ImageFlow.Uploaded gif = new ImageFlow(mockMvc).uploadPost(me, "image/gif", TestImages.gif(400, 300, 12),
                TestImages.webp(400, 300));
        assertThat(gif.url()).endsWith(".gif");
    }

    private void expectRejected(long me, String contentType, byte[] original, Long declaredSize, byte[] thumb, String detail)
            throws Exception {
        ImageFlow flow = new ImageFlow(mockMvc);
        ImageFlow.PostPresigned p = flow.presignPost(me, contentType, declaredSize == null ? original.length : declaredSize,
                thumb == null ? null : (long) thumb.length);
        assertThat(StorageTestClient.put(p.uploadUrl(), p.headers(), original)).isEqualTo(200);
        if (thumb != null) {
            assertThat(StorageTestClient.put(p.thumbUploadUrl(), p.thumbHeaders(), thumb)).isEqualTo(200);
        }
        String originalKey = key(p.imageId(), "storage_key");
        String thumbKey = key(p.imageId(), "thumb_storage_key");
        mockMvc.perform(completeRequest(me, p.imageId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"))
                .andExpect(jsonPath("$.detail").value(detail));
        assertThat(rowExists(p.imageId())).isFalse();
        assertThat(storage.inspect(originalKey)).isEmpty();
        if (thumbKey != null) {
            assertThat(storage.inspect(thumbKey)).isEmpty();
        }
    }

    @Test
    void serverRechecksAndDeletesBadUploads() throws Exception {
        long me = verified("badfiles");
        byte[] thumb = TestImages.webp(640, 400);
        // 형식 위장: PNG를 webp라고 신고
        expectRejected(me, "image/webp", TestImages.png(100, 100), null, thumb, "CONTENT_MISMATCH");
        // 실행 파일을 jpg로
        expectRejected(me, "image/jpeg", "MZ\u0090\u0000This program".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                null, thumb, "CONTENT_MISMATCH");
        // 신고보다 큰 파일
        expectRejected(me, "image/png", TestImages.png(100, 100), 50L, thumb, "SIZE");
        // 초대형 해상도
        expectRejected(me, "image/webp", TestImages.webp(10001, 10), null, thumb, "DIMENSION");
        // EXIF(위치 정보)가 남은 JPEG
        expectRejected(me, "image/jpeg", TestImages.jpegWithExif(200, 100), null, thumb, "METADATA");
        // 썸네일 규격: 가로 641, PNG 썸네일
        expectRejected(me, "image/webp", TestImages.webp(1200, 800), null, TestImages.webp(641, 400), "THUMBNAIL");
        expectRejected(me, "image/webp", TestImages.webp(1200, 800), null, TestImages.png(320, 200), "THUMBNAIL");
        // GIF: 1920px 초과, 300프레임 초과
        expectRejected(me, "image/gif", TestImages.gif(1921, 10, 2), null, thumb, "DIMENSION");
        expectRejected(me, "image/gif", TestImages.gif(8, 8, 301), null, thumb, "FRAMES");
    }

    @Test
    void presignLimitsTypeSizeAndAuthority() throws Exception {
        long me = verified("presignlim");
        mockMvc.perform(postPresignRequest(me, "image/bmp", 100, 10L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("TYPE"));
        mockMvc.perform(postPresignRequest(me, "image/webp", 10_485_761, 10L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("SIZE"));
        mockMvc.perform(postPresignRequest(me, "image/webp", 100, 1_048_577L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("THUMBNAIL"));
        mockMvc.perform(postPresignRequest(me, "image/webp", 10_485_760, 1_048_576L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbUploadUrl").exists())
                .andExpect(jsonPath("$.headers['Cache-Control']").value(ImageStorage.CACHE_CONTROL));
        long unverified = members.localMember("presignunv", "미인증", "presignunv@example.com", "Blog#2026ok", false);
        mockMvc.perform(postPresignRequest(unverified, "image/webp", 100, null)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // 남이 올린 사진은 완료할 수 없다(존재도 숨김)
        long other = verified("presignoth");
        ImageFlow.PostPresigned p = new ImageFlow(mockMvc).presignPost(other, "image/webp", 100, null);
        mockMvc.perform(completeRequest(me, p.imageId())).andExpect(status().isNotFound());
    }
}
