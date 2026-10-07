package com.team.blog.account.integration;

import static com.team.blog.account.integration.ProfileTestSupport.patchProfile;
import static com.team.blog.account.integration.ProfileTestSupport.q;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 003 T223: 하나라도 실패하면 아무것도 바뀌지 않고, 실패 칸을 모두 돌려준다(FR-005, SC-001, SC-002). */
class ProfileValidationIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT nickname, bio, nickname_changed_at FROM member WHERE id = ?", id);
    }

    @Test
    void longBioBlocksValidNicknameToo() throws Exception {
        long id = members.active("kim755030", "김민서");
        mockMvc.perform(patchProfile(id, "{\"nickname\":\"민서\",\"bio\":" + q("가".repeat(201)) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("입력한 내용을 확인해 주세요"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("bio"))
                .andExpect(jsonPath("$.errors[0].code").value("BIO_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].message").value("소개는 200자까지 쓸 수 있어요"));
        assertThat(row(id)).containsEntry("nickname", "김민서").containsEntry("bio", null);
        assertThat(row(id).get("nickname_changed_at")).isNull();
    }

    @Test
    void allFailingFieldsComeBackAtOnce() throws Exception {
        long id = members.active("kim755030", "김민서");
        String body = mockMvc.perform(patchProfile(id, "{\"nickname\":\"김 민서\",\"bio\":\"오늘 시1발\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("nickname", "bio")))
                .andExpect(jsonPath("$.errors[*].code").value(
                        containsInAnyOrder("NICKNAME_INVALID_FORMAT", "BIO_BANNED_WORD")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("시발").doesNotContain("시1발");
        assertThat(row(id)).containsEntry("nickname", "김민서").containsEntry("bio", null);
    }

    @Test
    void tooManyLinesAndBlankLineCollapse() throws Exception {
        long id = members.active("kim755030", "김민서");
        mockMvc.perform(patchProfile(id, "{\"bio\":" + q("1\n2\n3\n4\n5") + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("BIO_TOO_MANY_LINES"))
                .andExpect(jsonPath("$.errors[0].message").value("소개는 4줄까지 쓸 수 있어요"));
        mockMvc.perform(patchProfile(id, "{\"bio\":" + q("첫 줄\n\n\n\n\n셋째 줄") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("첫 줄\n\n셋째 줄"));
    }

    @Test
    void scriptInBioIsShownAsTextOnBlogHeader() throws Exception {
        long id = members.active("kim755030", "김민서");
        mockMvc.perform(patchProfile(id, "{\"bio\":" + q("<script>alert(1)</script> https://evil.example") + "}"))
                .andExpect(status().isOk());
        String html = mockMvc.perform(get("/@kim755030")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("href=\"https://evil.example\"");
    }

    @Test
    void wrongTypesAreReportedPerField() throws Exception {
        long id = members.active("kim755030", "김민서");
        mockMvc.perform(patchProfile(id, "{\"bio\":3,\"profileImageId\":\"abc\",\"nickname\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("bio", "profileImageId", "nickname")))
                .andExpect(jsonPath("$.errors[*].code").value(
                        containsInAnyOrder("INVALID_VALUE", "INVALID_VALUE", "INVALID_VALUE")));
        mockMvc.perform(patchProfile(id, "not json")).andExpect(status().isBadRequest());
    }
}
