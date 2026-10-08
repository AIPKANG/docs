package com.team.blog.account.integration;

import static com.team.blog.account.integration.ProfileTestSupport.patchProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.StorageTestClient;
import com.team.blog.support.TestImages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 003 T267: 소셜 가입 때 소셜 사진을 우리 저장소에 복사(US3, FR-019~FR-023, SC-005). */
class SocialPictureIT extends IntegrationTestBase {

    private static final String GOOGLE_PICTURE = "https://lh3.googleusercontent.com/a/ACg8ocKabc=s96-c";

    @Autowired
    JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder socialLogin(String provider, String id, String email, String picture) {
        MockHttpServletRequestBuilder builder = post("/test/social-login").with(csrf())
                .param("provider", provider).param("id", id).param("name", "Kim Minseo");
        if (email != null) {
            builder.param("email", email);
        }
        if (picture != null) {
            builder.param("picture", picture);
        }
        return builder;
    }

    private static MockHttpServletRequestBuilder complete(boolean usePicture, String email) {
        MockHttpServletRequestBuilder builder = post("/signup/social").with(csrf())
                .param("handle", "kimminseo").param("nickname", "김민서")
                .param("agreeTerms", "true").param("agreePrivacy", "true");
        if (usePicture) {
            builder.param("useSocialPicture", "true");
        }
        if (email != null) {
            builder.param("email", email);
        }
        return builder;
    }

    @Test
    void allowedPictureIsOfferedAt256AndCopiedThroughOurStorage() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(socialLogin("GOOGLE", "google-sub-1", "kim@gmail.com", GOOGLE_PICTURE));
        String form = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(form).contains("https://lh3.googleusercontent.com/a/ACg8ocKabc=s256-c")
                .containsPattern("name=\"useSocialPicture\"[^>]*checked");

        MockHttpServletResponse done = browser.perform(complete(true, null)).getResponse();
        assertThat(done.getStatus()).isEqualTo(303);
        assertThat(done.getHeader("Location")).isEqualTo("/settings/social-picture");

        String page = browser.perform(get("/settings/social-picture")).getResponse().getContentAsString();
        assertThat(page).contains("data-picture-url=\"https://lh3.googleusercontent.com/a/ACg8ocKabc=s256-c\"")
                .contains("/js/profile/social-picture.js")
                .contains("소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요");
        // 한 번만 넘긴다
        MockHttpServletResponse again = browser.perform(get("/settings/social-picture")).getResponse();
        assertThat(again.getStatus()).isEqualTo(303);
        assertThat(again.getHeader("Location")).isEqualTo("/");

        // 브라우저가 받아 256×256으로 바꾼 사진을 일반 업로드 흐름으로 복사(서버는 소셜 주소로 요청하지 않음)
        long memberId = jdbc.queryForObject("SELECT id FROM member WHERE handle = 'go-kimminseo'", Long.class);
        byte[] copy = TestImages.webp(256, 256);
        String presign = browser.perform(post("/api/images/presign").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"PROFILE\",\"contentType\":\"image/webp\",\"size\":" + copy.length + "}"))
                .getResponse().getContentAsString();
        long imageId = ((Number) com.jayway.jsonpath.JsonPath.read(presign, "$.imageId")).longValue();
        String uploadUrl = com.jayway.jsonpath.JsonPath.read(presign, "$.uploadUrl");
        assertThat(StorageTestClient.put(uploadUrl, Map.of("Content-Type", "image/webp", "Cache-Control", com.team.blog.media.application.ImageStorage.CACHE_CONTROL), copy)).isEqualTo(200);
        assertThat(browser.perform(post("/api/images/" + imageId + "/complete").with(csrf())).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(browser.perform(patchProfile(memberId, "{\"profileImageId\":" + imageId + "}")).getResponse().getStatus())
                .isEqualTo(200);

        String url = jdbc.queryForObject("SELECT profile_image_url FROM member WHERE id = ?", String.class, memberId);
        assertThat(url).startsWith(storageEndpoint() + "/blog-images/images/").doesNotContain("googleusercontent");
        List<String> everything = jdbc.queryForList("""
                SELECT coalesce(profile_image_url, '') || coalesce(bio, '') FROM member
                UNION ALL SELECT storage_key || original_name FROM image""", String.class);
        assertThat(everything).noneMatch(value -> value.contains("googleusercontent"));
    }

    @Test
    void uncheckedPictureGoesHomeWithDefaultIcon() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(socialLogin("GOOGLE", "google-sub-2", "lee@gmail.com", GOOGLE_PICTURE));
        MockHttpServletResponse done = browser.perform(complete(false, null)).getResponse();
        assertThat(done.getHeader("Location")).isEqualTo("/");
        assertThat(browser.perform(get("/settings/social-picture")).getResponse().getHeader("Location")).isEqualTo("/");
        String blog = mockMvc.perform(get("/@go-kimminseo")).andReturn().getResponse().getContentAsString();
        assertThat(blog).contains("avatar-default");
    }

    @Test
    void disallowedPictureHostIsNeverShown() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(socialLogin("GOOGLE", "google-sub-3", "park@gmail.com", "https://evil.example/p.png"));
        String form = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(form).doesNotContain("evil.example").doesNotContain("name=\"useSocialPicture\"");
        browser.perform(socialLogin("GOOGLE", "google-sub-3", "park@gmail.com", "http://lh3.googleusercontent.com/a/x"));
        form = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(form).doesNotContain("lh3.googleusercontent.com/a/x");
        assertThat(browser.perform(complete(true, null)).getResponse().getHeader("Location")).isEqualTo("/");
    }

    @Test
    void githubWithoutVerifiedEmailSkipsPictureCopy() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(socialLogin("GITHUB", "777", null, "https://avatars.githubusercontent.com/u/777?v=4"));
        String form = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(form).contains("https://avatars.githubusercontent.com/u/777?v=4&amp;s=256");
        MockHttpServletResponse done = browser.perform(complete(true, "gh777@example.com")).getResponse();
        assertThat(done.getHeader("Location")).isEqualTo("/");
    }

    @Test
    void socialPicturePageRequiresLogin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/settings/social-picture")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).startsWith("/login");
    }
}
