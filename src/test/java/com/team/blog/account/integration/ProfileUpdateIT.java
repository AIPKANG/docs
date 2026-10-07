package com.team.blog.account.integration;

import static com.team.blog.account.integration.ProfileTestSupport.patchProfile;
import static com.team.blog.account.integration.ProfileTestSupport.q;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 003 T222: 닉네임·소개 한 번에 저장(US1, FR-001~FR-008). */
class ProfileUpdateIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private long member() {
        return members.localMember("kim755030", "김민서", "kim755030@naver.com", "Blog#2026ok", true);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT nickname, bio, nickname_changed_at, handle FROM member WHERE id = ?", id);
    }

    @Test
    void savesNicknameAndBioTogetherAndShowsThemImmediately() throws Exception {
        long id = member();
        mockMvc.perform(patchProfile(id, "{\"nickname\":\"민서\",\"bio\":" + q("백엔드 개발을\n공부하고 있어요.") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("민서"))
                .andExpect(jsonPath("$.bio").value("백엔드 개발을\n공부하고 있어요."))
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.email").value("kim755030@naver.com"));
        assertThat(row(id)).containsEntry("nickname", "민서").containsEntry("bio", "백엔드 개발을\n공부하고 있어요.");

        mockMvc.perform(get("/api/me/profile").with(TestAuth.member(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("민서"))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.defaultVisibility").value("PUBLIC"));

        String blog = mockMvc.perform(get("/@kim755030")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(blog).contains("민서").contains("백엔드 개발을\n공부하고 있어요.").contains("profile-bio");
    }

    @Test
    void onlySentFieldsChange() throws Exception {
        long id = member();
        mockMvc.perform(patchProfile(id, "{\"bio\":\"소개만\"}")).andExpect(status().isOk());
        assertThat(row(id)).containsEntry("nickname", "김민서").containsEntry("bio", "소개만");
        assertThat(row(id).get("nickname_changed_at")).isNull();

        mockMvc.perform(patchProfile(id, "{\"nickname\":\"민서\"}")).andExpect(status().isOk());
        assertThat(row(id)).containsEntry("nickname", "민서").containsEntry("bio", "소개만");

        mockMvc.perform(patchProfile(id, "{\"bio\":null}")).andExpect(status().isOk());
        assertThat(row(id).get("bio")).isNull();

        mockMvc.perform(patchProfile(id, "{\"bio\":\"   \"}")).andExpect(status().isOk());
        assertThat(row(id).get("bio")).isNull();
    }

    @Test
    void bioOnlySavesDuringNicknameCooldownButNicknameChangeFailsEverything() throws Exception {
        long id = member();
        Instant changedAt = clock.instant().minus(Duration.ofDays(1));
        members.setNicknameChangedAt(id, changedAt);

        mockMvc.perform(patchProfile(id, "{\"bio\":\"제한 중에도 소개는 저장\"}")).andExpect(status().isOk());
        assertThat(row(id)).containsEntry("bio", "제한 중에도 소개는 저장");

        mockMvc.perform(patchProfile(id, "{\"nickname\":\"민서\",\"bio\":\"바뀌면 안 됨\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_CHANGE_TOO_SOON"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.startsWith("다음 변경 가능일: ")))
                .andExpect(jsonPath("$.errors[0].nextAllowedAt").exists());
        assertThat(row(id)).containsEntry("nickname", "김민서").containsEntry("bio", "제한 중에도 소개는 저장");

        // 지금 닉네임을 그대로 보내면 변경이 아니다
        mockMvc.perform(patchProfile(id, "{\"nickname\":\" 김민서 \",\"bio\":\"그대로 보내도 됨\"}"))
                .andExpect(status().isOk());
        assertThat(row(id)).containsEntry("bio", "그대로 보내도 됨");
    }

    @Test
    void handleAndEmailCannotBeChanged() throws Exception {
        long id = member();
        mockMvc.perform(patchProfile(id, "{\"handle\":\"hacked\",\"email\":\"x@y.z\",\"bio\":\"a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.email").value("kim755030@naver.com"));
        assertThat(row(id)).containsEntry("handle", "kim755030");
        assertThat(jdbc.queryForObject("SELECT email FROM auth_identity WHERE member_id = ?", String.class, id))
                .isEqualTo("kim755030@naver.com");
    }

    @Test
    void guestsAreRejectedAndSettingsRedirectsToLogin() throws Exception {
        mockMvc.perform(patch("/api/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        mockMvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/settings"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?redirect=%2Fsettings"));
    }

    @Test
    void settingsPageShowsCurrentProfileWithReadOnlyAddress() throws Exception {
        long id = member();
        mockMvc.perform(patchProfile(id, "{\"bio\":\"<b>굵게</b>\"}")).andExpect(status().isOk());
        String html = mockMvc.perform(get("/settings").with(TestAuth.member(id)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("value=\"김민서\"").contains("&lt;b&gt;굵게&lt;/b&gt;").contains("@kim755030")
                .contains("변경할 수 없어요").contains("/js/profile/settings.js");
    }

    @Test
    void unverifiedMemberCanEditNicknameAndBio() throws Exception {
        long id = members.localMember("lee755030", "이서준", "lee755030@naver.com", "Blog#2026ok", false);
        mockMvc.perform(patchProfile(id, "{\"nickname\":\"서준\",\"bio\":\"인증 전\"}")).andExpect(status().isOk());
        assertThat(row(id)).containsEntry("nickname", "서준").containsEntry("bio", "인증 전");
    }
}
