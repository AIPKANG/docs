package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 003 T257: 설정 화면 표시(11 §2, FR-003, FR-024 화면, FR-029). */
class SettingsPageIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private String page(long memberId) throws Exception {
        return mockMvc.perform(get("/settings").with(TestAuth.member(memberId))).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void emailMemberSeesPasswordFormAndReadOnlyAccountInfo() throws Exception {
        long id = members.localMember("kim755030", "김민서", "kim755030@naver.com", "Blog#2026ok", true);
        jdbc.update("UPDATE member SET default_visibility = 'PRIVATE' WHERE id = ?", id);
        String html = page(id);
        assertThat(html).contains("id=\"password-form\"").contains("kim755030@naver.com").contains("@kim755030")
                .contains("변경할 수 없어요").contains(">이메일<")
                .contains("value=\"PRIVATE\" checked").contains("id=\"withdraw-link\"")
                .contains("id=\"profile-image-pick\"").doesNotContain("이메일 인증 후 사진을 올릴 수 있어요");
    }

    @Test
    void socialMemberHasNoPasswordForm() throws Exception {
        long id = members.active("gi-kim755030", "김민서");
        members.addSocialIdentity(id, "GITHUB", "12345", "kim@github.test", Instant.now());
        String html = page(id);
        assertThat(html).doesNotContain("id=\"password-form\"").contains("GitHub");
    }

    @Test
    void nicknameIsDisabledDuringCooldownWithNextDate() throws Exception {
        long id = members.localMember("kim755030", "김민서", "kim755030@naver.com", "Blog#2026ok", true);
        members.setNicknameChangedAt(id, clock.instant().minus(Duration.ofDays(1)));
        String html = page(id);
        assertThat(html).contains("다음 변경 가능일: ").containsPattern("id=\"nickname\"[^>]*disabled");
    }

    @Test
    void unverifiedMemberSeesNoticeInsteadOfImageButton() throws Exception {
        long id = members.localMember("lee755030", "이서준", "lee755030@naver.com", "Blog#2026ok", false);
        String html = page(id);
        assertThat(html).contains("이메일 인증 후 사진을 올릴 수 있어요").doesNotContain("id=\"profile-image-pick\"");
    }
}
