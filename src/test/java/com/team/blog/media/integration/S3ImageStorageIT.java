package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.application.StoredObject;
import com.team.blog.media.application.UploadTarget;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.StorageTestClient;
import com.team.blog.support.TestImages;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 003 T216: 실제 MinIO 포크에 사전 서명 PUT → 확인 → 익명 읽기 → 삭제. */
class S3ImageStorageIT extends IntegrationTestBase {

    @Autowired
    ImageStorage storage;

    @Test
    void presignedPutInspectPublicReadAndDelete() {
        String key = "images/2026/10/" + UUID.randomUUID() + ".png";
        byte[] png = TestImages.png(256, 256);
        UploadTarget target = storage.prepareUpload(key, "image/png", png.length);
        assertThat(target.method()).isEqualTo("PUT");
        assertThat(target.url()).contains("X-Amz-Signature").contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");

        assertThat(StorageTestClient.put(target.url(), target.headers(), png)).isEqualTo(200);

        StoredObject stored = storage.inspect(key).orElseThrow();
        assertThat(stored.size()).isEqualTo(png.length);
        assertThat(stored.head()).startsWith(png[0], png[1], png[2], png[3]);
        assertThat(StorageTestClient.get(storage.publicUrl(key))).isEqualTo(200);

        storage.delete(key);
        assertThat(storage.inspect(key)).isEmpty();
        storage.delete(key);
    }

    @Test
    void contentTypeOtherThanSignedIsRejected() {
        String key = "images/2026/10/" + UUID.randomUUID() + ".webp";
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        int status = StorageTestClient.put(target.url(), Map.of("Content-Type", "image/png", "Cache-Control", ImageStorage.CACHE_CONTROL), TestImages.png(8, 8));
        assertThat(status).isEqualTo(403);
        assertThat(storage.inspect(key)).isEmpty();
    }

    @Test
    void anonymousReadOutsideImagesAndListingAreDenied() {
        String bucketUrl = storageEndpoint() + "/blog-images";
        assertThat(StorageTestClient.get(bucketUrl + "?list-type=2")).isEqualTo(403);
        assertThat(StorageTestClient.get(bucketUrl + "/secret.txt")).isEqualTo(403);
    }
}
