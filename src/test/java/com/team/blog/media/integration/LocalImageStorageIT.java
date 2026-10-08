package com.team.blog.media.integration;

import static com.team.blog.support.ImageFlow.completeRequest;
import static com.team.blog.support.ImageFlow.postPresignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.infra.LocalImageStorage;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** 008 T813: 저장소를 띄울 수 없는 환경의 대체 저장소(FR-015). */
@TestPropertySource(properties = {"blog.storage.type=local", "blog.storage.local-dir=build/test-local-images",
        "blog.storage.local-secret=test-local-secret"})
class LocalImageStorageIT extends IntegrationTestBase {

    @Autowired
    ImageStorage storage;

    @Test
    void uploadsThroughSignedLocalUrlAndServesWithLongCache() throws Exception {
        assertThat(storage).isInstanceOf(LocalImageStorage.class);
        long me = members.localMember("localup", "로컬", "localup@example.com", "Blog#2026ok", true);
        byte[] original = TestImages.webp(800, 600);
        byte[] thumb = TestImages.webp(640, 480);
        String json = mockMvc.perform(postPresignRequest(me, "image/webp", original.length, (long) thumb.length))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.imageId")).longValue();
        String uploadUrl = JsonPath.read(json, "$.uploadUrl");
        String thumbUrl = JsonPath.read(json, "$.thumbUploadUrl");
        assertThat(uploadUrl).startsWith("/api/images/local-upload?key=images%2F");

        // 서명이 틀리거나 Content-Type이 다르면 거부
        mockMvc.perform(put(java.net.URI.create(uploadUrl.replaceAll("sig=[0-9a-f]{4}", "sig=0000"))).contentType("image/webp").content(original))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(java.net.URI.create(uploadUrl)).contentType("image/png").content(original)).andExpect(status().isForbidden());

        mockMvc.perform(put(java.net.URI.create(uploadUrl)).contentType("image/webp").content(original)).andExpect(status().isOk());
        mockMvc.perform(put(java.net.URI.create(thumbUrl)).contentType("image/webp").content(thumb)).andExpect(status().isOk());
        String done = mockMvc.perform(completeRequest(me, id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(done, "$.url");
        assertThat(url).startsWith("/media/images/");
        byte[] served = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", ImageStorage.CACHE_CONTROL))
                .andExpect(header().string("Content-Type", "image/webp"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(original);
        mockMvc.perform(get("/media/images/../../build.gradle.kts")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/media/images/nope.webp")).andExpect(status().isNotFound());
    }
}
