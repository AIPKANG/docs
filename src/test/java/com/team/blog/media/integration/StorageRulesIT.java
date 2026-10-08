package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.application.UploadTarget;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.StorageTestClient;
import com.team.blog.support.TestImages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 008 T812: 저장소 접근 규칙(FR-011, FR-012) — 고정한 MinIO 포크 이미지로 실제 확인. */
class StorageRulesIT extends IntegrationTestBase {

    @Autowired
    ImageStorage storage;

    private static String newKey() {
        return "images/2026/10/" + UUID.randomUUID() + ".webp";
    }

    @Test
    void forgedSignatureIsRejected() {
        String key = newKey();
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        String forged = target.url().replaceAll("(X-Amz-Signature=)[0-9a-f]{4}", "$10000");
        assertThat(StorageTestClient.put(forged, target.headers(), TestImages.webp(8, 8))).isEqualTo(403);
        assertThat(storage.inspect(key)).isEmpty();
    }

    @Test
    void changedPathAfterSigningIsRejected() {
        String key = newKey();
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        String moved = target.url().replace(key, newKey());
        assertThat(StorageTestClient.put(moved, target.headers(), TestImages.webp(8, 8))).isEqualTo(403);
    }

    @Test
    void unsignedAnonymousPutIsRejected() {
        String key = newKey();
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        String unsigned = target.url().substring(0, target.url().indexOf('?'));
        Map<String, String> headers = new HashMap<>(target.headers());
        assertThat(StorageTestClient.put(unsigned, headers, TestImages.webp(8, 8))).isEqualTo(403);
        assertThat(storage.inspect(key)).isEmpty();
    }

    @Test
    void expiredSignatureIsRejected() {
        String key = newKey();
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        // X-Amz-Date를 하루 전으로 바꾸면 서명이 맞지 않거나 만료로 거부된다(어느 쪽이든 403)
        String old = target.url().replaceAll("X-Amz-Date=(\\d{8})", "X-Amz-Date=20000101");
        assertThat(StorageTestClient.put(old, target.headers(), TestImages.webp(8, 8))).isEqualTo(403);
    }

    @Test
    void cacheControlIsPartOfSignature() {
        String key = newKey();
        UploadTarget target = storage.prepareUpload(key, "image/webp", 100);
        assertThat(target.headers()).containsEntry("Cache-Control", ImageStorage.CACHE_CONTROL);
        Map<String, String> wrong = new HashMap<>(target.headers());
        wrong.put("Cache-Control", "no-cache");
        assertThat(StorageTestClient.put(target.url(), wrong, TestImages.webp(8, 8))).isEqualTo(403);
    }
}
