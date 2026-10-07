package com.team.blog.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T124: 쓰기 가드(FR-011, FR-032, SC-002, 42 §3·§4). 테스트 전용 엔드포인트 WriteProbeController 사용. */
class WriteGuardIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM tag", Integer.class);
    }

    @Test
    void unverifiedMemberIsRefusedWithEmailNotVerifiedAndNothingIsCreated() throws Exception {
        long id = members.localMember("unver", "미인증", "unver@x.com", "Blog#2026ok", false);

        MvcResult rest = mockMvc.perform(post("/api/test/write").with(csrf()).with(TestAuth.member(id))).andReturn();
        assertThat(rest.getResponse().getStatus()).isEqualTo(403);
        assertThat(rest.getResponse().getContentAsString()).contains("\"code\":\"EMAIL_NOT_VERIFIED\"");

        MvcResult ssr = mockMvc.perform(post("/test/write").with(csrf()).with(TestAuth.member(id))).andReturn();
        assertThat(ssr.getResponse().getStatus()).isEqualTo(403);
        assertThat(ssr.getResponse().getContentAsString()).contains("이메일 인증 후 이용할 수 있어요")
                .contains("인증 메일 다시 보내기").contains("action=\"/auth/verify/resend\"");
        assertThat(rows()).isZero();
    }

    @Test
    void verifiedMemberCanWrite() throws Exception {
        long id = members.localMember("verified", "인증됨", "ok@x.com", "Blog#2026ok", true);
        MvcResult result = mockMvc.perform(post("/api/test/write").with(csrf()).with(TestAuth.member(id))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void anonymousGets401OrLoginRedirect() throws Exception {
        MvcResult rest = mockMvc.perform(post("/api/test/write").with(csrf())).andReturn();
        assertThat(rest.getResponse().getStatus()).isEqualTo(401);
        assertThat(rest.getResponse().getContentAsString()).contains("\"code\":\"LOGIN_REQUIRED\"");

        MvcResult ssr = mockMvc.perform(post("/test/write").with(csrf())).andReturn();
        assertThat(ssr.getResponse().getStatus()).isEqualTo(303);
        assertThat(ssr.getResponse().getHeader("Location")).isEqualTo("/login?redirect=%2Ftest%2Fwrite");
        assertThat(rows()).isZero();
    }

    @Test
    void withdrawingMemberIsSentToRestore() throws Exception {
        long id = members.withdrawing("leaving", "떠나요", Instant.now());
        members.addLocalIdentity(id, "leaving@x.com", "Blog#2026ok", Instant.now());

        MvcResult rest = mockMvc.perform(post("/api/test/write").with(csrf()).with(TestAuth.member(id))).andReturn();
        assertThat(rest.getResponse().getStatus()).isEqualTo(403);
        assertThat(rest.getResponse().getContentAsString()).contains("\"code\":\"ACCOUNT_WITHDRAWN\"");

        MvcResult ssr = mockMvc.perform(post("/test/write").with(csrf()).with(TestAuth.member(id))).andReturn();
        assertThat(ssr.getResponse().getStatus()).isEqualTo(303);
        assertThat(ssr.getResponse().getHeader("Location")).isEqualTo("/account/restore");
        assertThat(rows()).isZero();
    }

    @Test
    void verificationIsReadFromTheDatabaseNotTheSession() throws Exception {
        long id = members.localMember("otherdev", "다른기기", "dev@x.com", "Blog#2026ok", false);
        assertThat(mockMvc.perform(post("/api/test/write").with(csrf()).with(TestAuth.member(id)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        // 다른 기기에서 인증 완료
        jdbc.update("UPDATE auth_identity SET email_verified_at = now() WHERE member_id = ?", id);
        assertThat(mockMvc.perform(post("/api/test/write").with(csrf()).with(TestAuth.member(id)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
