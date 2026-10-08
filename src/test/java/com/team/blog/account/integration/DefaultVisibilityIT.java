package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.AccountSettingsService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 003 T256: 새 글 기본 공개 범위(FR-028). */
class DefaultVisibilityIT extends IntegrationTestBase {

    @Autowired
    AccountSettingsService accountSettingsService;

    private static MockHttpServletRequestBuilder settings(String json) {
        return patch("/api/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void defaultIsPublicAndCanBeChangedToPrivate() throws Exception {
        long id = members.active("kim755030", "김민서");
        assertThat(accountSettingsService.defaultVisibility(id)).isEqualTo("PUBLIC");
        mockMvc.perform(settings("{\"defaultVisibility\":\"PRIVATE\"}").with(TestAuth.member(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVisibility").value("PRIVATE"));
        assertThat(accountSettingsService.defaultVisibility(id)).isEqualTo("PRIVATE");
        mockMvc.perform(get("/api/me/profile").with(TestAuth.member(id)))
                .andExpect(jsonPath("$.defaultVisibility").value("PRIVATE"));
        mockMvc.perform(settings("{\"defaultVisibility\":\"PUBLIC\"}").with(TestAuth.member(id))).andExpect(status().isOk());
        assertThat(accountSettingsService.defaultVisibility(id)).isEqualTo("PUBLIC");
    }

    @Test
    void unsupportedValuesAreRejected() throws Exception {
        long id = members.active("kim755030", "김민서");
        for (String json : new String[] {"{\"defaultVisibility\":\"ALL\"}",
                "{\"defaultVisibility\":null}", "{}", "{\"defaultVisibility\":1}"}) {
            mockMvc.perform(settings(json).with(TestAuth.member(id)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("defaultVisibility"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_VISIBILITY"));
        }
        assertThat(accountSettingsService.defaultVisibility(id)).isEqualTo("PUBLIC");
    }

    @Test
    void guestsAreRejected() throws Exception {
        mockMvc.perform(settings("{\"defaultVisibility\":\"PRIVATE\"}")).andExpect(status().isUnauthorized());
    }
}
