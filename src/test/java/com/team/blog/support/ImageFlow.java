package com.team.blog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 브라우저의 프로필 이미지 업로드 흐름(presign → 저장소 PUT → complete)을 그대로 흉내 내는 테스트 도우미(003).
 */
public final class ImageFlow {

    private final MockMvc mockMvc;

    public ImageFlow(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public record Presigned(long imageId, String uploadUrl, Map<String, String> headers) {
    }

    public static MockHttpServletRequestBuilder presignRequest(long memberId, String contentType, long size) {
        return presignRequest(memberId, "PROFILE", contentType, size);
    }

    public static MockHttpServletRequestBuilder presignRequest(long memberId, String purpose, String contentType, long size) {
        return post("/api/images/presign").with(csrf()).with(TestAuth.member(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"" + purpose + "\",\"contentType\":\"" + contentType + "\",\"size\":" + size
                        + ",\"originalName\":\"내 사진.jpg\"}");
    }

    public static MockHttpServletRequestBuilder completeRequest(long memberId, long imageId) {
        return post("/api/images/" + imageId + "/complete").with(csrf()).with(TestAuth.member(memberId));
    }

    public Presigned presign(long memberId, String contentType, long size) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(presignRequest(memberId, contentType, size)).andReturn().getResponse();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("presign failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        String json = response.getContentAsString();
        Number id = JsonPath.read(json, "$.imageId");
        Map<String, String> headers = JsonPath.read(json, "$.headers");
        return new Presigned(id.longValue(), JsonPath.read(json, "$.uploadUrl"), headers);
    }

    /** presign → PUT → complete. @return 완료된 이미지 ID */
    public long upload(long memberId, String contentType, byte[] bytes) throws Exception {
        Presigned presigned = presign(memberId, contentType, bytes.length);
        int put = StorageTestClient.put(presigned.uploadUrl(), presigned.headers(), bytes);
        if (put != 200) {
            throw new IllegalStateException("put failed: " + put);
        }
        MockHttpServletResponse response = mockMvc.perform(completeRequest(memberId, presigned.imageId())).andReturn().getResponse();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("complete failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        return presigned.imageId();
    }

    // ----- 008: 글 사진(원본 + 썸네일) -----

    public static MockHttpServletRequestBuilder postPresignRequest(long memberId, String contentType, long size, Long thumbSize) {
        return post("/api/images/presign").with(csrf()).with(TestAuth.member(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"POST\",\"contentType\":\"" + contentType + "\",\"size\":" + size
                        + (thumbSize == null ? "" : ",\"thumbSize\":" + thumbSize) + ",\"originalName\":\"휴가 사진.jpg\"}");
    }

    public record PostPresigned(long imageId, String uploadUrl, Map<String, String> headers, String thumbUploadUrl,
                                Map<String, String> thumbHeaders) {
    }

    public record Uploaded(long imageId, String url, String thumbUrl) {
    }

    public PostPresigned presignPost(long memberId, String contentType, long size, Long thumbSize) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(postPresignRequest(memberId, contentType, size, thumbSize))
                .andReturn().getResponse();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("presign failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        String json = response.getContentAsString();
        Number id = JsonPath.read(json, "$.imageId");
        String thumbUrl = thumbSize == null ? null : JsonPath.read(json, "$.thumbUploadUrl");
        Map<String, String> thumbHeaders = thumbSize == null ? null : JsonPath.read(json, "$.thumbHeaders");
        return new PostPresigned(id.longValue(), JsonPath.read(json, "$.uploadUrl"), JsonPath.read(json, "$.headers"),
                thumbUrl, thumbHeaders);
    }

    /** presign → 원본·썸네일 PUT → complete. */
    public Uploaded uploadPost(long memberId, String contentType, byte[] bytes, byte[] thumb) throws Exception {
        PostPresigned p = presignPost(memberId, contentType, bytes.length, thumb == null ? null : (long) thumb.length);
        if (StorageTestClient.put(p.uploadUrl(), p.headers(), bytes) != 200) {
            throw new IllegalStateException("put failed");
        }
        if (thumb != null && StorageTestClient.put(p.thumbUploadUrl(), p.thumbHeaders(), thumb) != 200) {
            throw new IllegalStateException("thumb put failed");
        }
        MockHttpServletResponse response = mockMvc.perform(completeRequest(memberId, p.imageId())).andReturn().getResponse();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("complete failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        String json = response.getContentAsString();
        String thumbUrl = thumb == null ? null : JsonPath.read(json, "$.thumbUrl");
        return new Uploaded(p.imageId(), JsonPath.read(json, "$.url"), thumbUrl);
    }

    /** 1200×800 WebP 원본 + 640×427 WebP 썸네일. */
    public Uploaded uploadPostWebp(long memberId) throws Exception {
        return uploadPost(memberId, "image/webp", TestImages.webp(1200, 800), TestImages.webp(640, 427));
    }

    /** 256×256 WebP 프로필 이미지 한 장. */
    public long uploadProfile(long memberId) throws Exception {
        return upload(memberId, "image/webp", TestImages.webp(256, 256));
    }
}
