package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** 실시간 확인 API (contracts/web-routes.md §1). */
@ExtendWith(OutputCaptureExtension.class)
class AvailabilityApiIT extends IntegrationTestBase {

    @Autowired
    StringRedisTemplate redis;

    // ----- 블로그 주소 -----

    @Test
    void handleAvailableReturnsNullReasonAndSuggestion() throws Exception {
        mockMvc.perform(get("/api/handles/availability").param("handle", "kim755030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()))
                .andExpect(jsonPath("$.suggestion").value(nullValue()));
    }

    @Test
    void handleReasons() throws Exception {
        members.active("kim755030", "김민서");
        expectHandle("kim-min", "INVALID_FORMAT", null);
        expectHandle("ab", "INVALID_FORMAT", null);
        expectHandle("admin", "RESERVED", "admin_2");
        expectHandle("sh1t", "BANNED_WORD", null);
        expectHandle("kim755030", "DUPLICATE", "kim755030_2");
    }

    @Test
    void bannedHandleResponseDoesNotContainTheWord() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/handles/availability").param("handle", "sh1tkim")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContainIgnoringCase("shit").doesNotContainIgnoringCase("sh1t");
    }

    @Test
    void suggestionPrefillsLocalHandleFromEmailInBody(CapturedOutput output) throws Exception {
        members.active("kim755030", "김민서");
        mockMvc.perform(post("/api/handles/suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kim755030@daum.net\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("kim755030_2"));
        mockMvc.perform(post("/api/handles/suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value(nullValue()));
        assertThat(output.getAll()).doesNotContain("kim755030@daum.net");
        assertThat(redis.keys("*")).allMatch(k -> !k.contains("@"));
    }

    @Test
    void suggestionRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/handles/suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kim@naver.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleBucketIsSharedAndLimitedTo30PerMinute() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/handles/availability").param("handle", "kim" + i)).andExpect(status().isOk());
            mockMvc.perform(post("/api/handles/suggestion").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"kim@x.com\"}")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/handles/availability").param("handle", "kim"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        mockMvc.perform(post("/api/handles/suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"kim@x.com\"}"))
                .andExpect(status().isTooManyRequests());
        Long ttl = redis.getExpire("account:handle-check:ip:127.0.0.1");
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(60);
    }

    // ----- 닉네임 -----

    @Test
    void nicknameAvailable() throws Exception {
        mockMvc.perform(get("/api/nicknames/availability").param("nickname", "김민서"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.code").value(nullValue()));
    }

    @Test
    void nicknameCodes() throws Exception {
        members.active("kim755030", "Kim");
        expectNickname("김 민서", "NICKNAME_INVALID_FORMAT");
        expectNickname("12345", "NICKNAME_LETTER_REQUIRED");
        expectNickname("관리자김", "NICKNAME_RESERVED");
        expectNickname("시1발", "NICKNAME_BANNED_WORD");
        expectNickname("KIM", "NICKNAME_DUPLICATE");
    }

    @Test
    void loggedInMemberIsExcludedFromDuplicate() throws Exception {
        long me = members.active("kim755030", "Kim");
        mockMvc.perform(get("/api/nicknames/availability").param("nickname", "kim").with(TestAuth.member(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        long other = members.active("lee755030", "Lee");
        mockMvc.perform(get("/api/nicknames/availability").param("nickname", "kim").with(TestAuth.member(other)))
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATE"));
    }

    @Test
    void bannedNicknameResponseDoesNotContainTheWord() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/nicknames/availability").param("nickname", "병1신왕")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("병신").doesNotContain("병1신");
    }

    @Test
    void nicknameBucketIsSeparateAndLimitedTo30PerMinute() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/nicknames/availability").param("nickname", "김민서")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/nicknames/availability").param("nickname", "김민서"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // 주소 버킷과 별도
        mockMvc.perform(get("/api/handles/availability").param("handle", "kim")).andExpect(status().isOk());
        assertThat(redis.getExpire("account:nickname-check:ip:127.0.0.1")).isPositive().isLessThanOrEqualTo(60);
    }

    private void expectNickname(String nickname, String code) throws Exception {
        mockMvc.perform(get("/api/nicknames/availability").param("nickname", nickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }

    private void expectHandle(String handle, String reason, String suggestion) throws Exception {
        mockMvc.perform(get("/api/handles/availability").param("handle", handle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(suggestion == null
                        ? jsonPath("$.suggestion").value(nullValue())
                        : jsonPath("$.suggestion").value(suggestion));
    }
}
