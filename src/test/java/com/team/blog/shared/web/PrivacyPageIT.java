package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** 016 FR-018·021 FR-035: 처리방침에 방문자 쿠키·외부 AI 전송 안내, 모든 화면 아래와 가입 화면에서 링크. */
class PrivacyPageIT extends IntegrationTestBase {

    @Test
    void privacyPageExplainsVisitorCookieAndExternalAi() throws Exception {
        String html = mockMvc.perform(get("/privacy")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("조회수 중복 방지용 무작위 식별자").contains("Google Gemini API(무료 등급)")
                .contains("사람이 읽고 검토할 수 있어요").contains("외부로 전송되지 않아요");
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString()).contains("href=\"/privacy\"");
        assertThat(mockMvc.perform(get("/signup")).andReturn().getResponse().getContentAsString()).contains("처리방침 보기");
    }
}
