package com.team.blog.account.integration;

import static com.team.blog.account.integration.SocialTestSupport.completeSocial;
import static com.team.blog.account.integration.SocialTestSupport.socialLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.Browser;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MailpitClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 001 T153: 소셜 가입 마무리(FR-021~FR-023, FR-033). */
class SocialSignupIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private int memberCount() {
        return jdbc.queryForObject("SELECT count(*) FROM member", Integer.class);
    }

    private Browser pendingGoogle(String sub, String email, String name) throws Exception {
        Browser browser = new Browser(mockMvc);
        assertThat(browser.perform(socialLogin("GOOGLE", sub, email, name)).getResponse().getHeader("Location"))
                .isEqualTo("/signup/social");
        return browser;
    }

    @Test
    void completionPagePrefillsNicknameAndFixedPrefixHandle() throws Exception {
        Browser browser = pendingGoogle("g-1", "kimmin@gmail.com", "Kim Min-seo");
        String page = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(page).contains("value=\"KimMinseo\"");
        assertThat(page).contains("<strong>go-</strong>").contains("data-prefix-locked=\"go-\"").contains("value=\"kimmin\"");
        assertThat(page).contains("이용약관에 동의해요").contains("개인정보 수집·이용에 동의해요").contains("프로필 사진 사용");
        assertThat(page).doesNotContain("이 이메일로 가입한 계정이 이미 있어요");

        Browser shortName = pendingGoogle("g-2", "x2@gmail.com", "A");
        String page2 = shortName.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(page2).contains("닉네임을 입력해 주세요").contains("id=\"nickname\"");
    }

    @Test
    void completionCreatesVerifiedGoogleAccountAndLogsIn() throws Exception {
        Browser browser = pendingGoogle("g-sub-77", "Kim@Gmail.com", "Kim Min-seo");
        MvcResult result = browser.perform(completeSocial("kim", "KimMinseo"));
        assertThat(result.getResponse().getStatus()).isEqualTo(303);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT a.provider, a.provider_user_id, a.email, a.password_hash, a.email_verified_at = m.created_at AS same_time,
                       m.handle, m.nickname
                FROM auth_identity a JOIN member m ON m.id = a.member_id""");
        assertThat(row).containsEntry("provider", "GOOGLE").containsEntry("provider_user_id", "g-sub-77")
                .containsEntry("email", "kim@gmail.com").containsEntry("password_hash", null)
                .containsEntry("same_time", true).containsEntry("handle", "go-kim").containsEntry("nickname", "KimMinseo");
        assertThat(jdbc.queryForList("SELECT type FROM member_agreement", String.class)).containsExactlyInAnyOrder("TERMS", "PRIVACY");
        assertThat(browser.perform(get("/signup/verify-sent")).getResponse().getStatus()).isEqualTo(200);
        // 대기 정보는 지워졌다
        assertThat(browser.perform(get("/signup/social")).getResponse().getHeader("Location")).isEqualTo("/login?social");

        // 같은 Google 계정으로 다시 로그인 → 새 계정 없음
        Browser again = new Browser(mockMvc);
        assertThat(again.perform(socialLogin("GOOGLE", "g-sub-77", "kim@gmail.com", "Kim")).getResponse().getHeader("Location"))
                .isEqualTo("/");
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void handleBodyWithAnotherProvidersPrefixIsRejected() throws Exception {
        Browser browser = pendingGoogle("g-3", "p@gmail.com", "Prefix");
        MvcResult result = browser.perform(completeSocial("gi-kim", "프리픽스"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("가입 방법과 맞지 않는 주소예요");
        assertThat(memberCount()).isZero();
    }

    @Test
    void sameEmailLocalAccountIsMentionedButNotMerged() throws Exception {
        members.localMember("samelocal", "로컬계정", "same@x.com", "Blog#2026ok", true);
        Browser browser = pendingGoogle("g-4", "same@x.com", "Same");
        String page = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(page).contains("이 이메일로 가입한 계정이 이미 있어요.").contains("기존 계정으로 로그인")
                .contains("action=\"/signup/social/cancel\"").contains("새 계정 만들기");

        // [새 계정 만들기] → 별도 계정
        MvcResult result = browser.perform(completeSocial("samegoogle", "구글계정"));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");
        assertThat(memberCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT member_id) FROM auth_identity WHERE email = 'same@x.com'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void cancelGoesToLoginWithoutCreatingAnything() throws Exception {
        members.localMember("samelocal2", "로컬둘", "same2@x.com", "Blog#2026ok", true);
        Browser browser = pendingGoogle("g-5", "same2@x.com", "Same Two");
        MvcResult cancel = browser.perform(post("/signup/social/cancel").with(csrf()));
        assertThat(cancel.getResponse().getHeader("Location")).isEqualTo("/login");
        assertThat(memberCount()).isEqualTo(1);
        assertThat(browser.perform(get("/signup/social")).getResponse().getHeader("Location")).isEqualTo("/login?social");
    }

    @Test
    void noNoticeForUnverifiedSocialEmailOrWithdrawingAccounts() throws Exception {
        long leaving = members.withdrawing("leavinglocal", "탈퇴중", Instant.now());
        members.addLocalIdentity(leaving, "gone@x.com", "Blog#2026ok", Instant.now());
        Browser withdrawn = pendingGoogle("g-6", "gone@x.com", "Gone");
        assertThat(withdrawn.perform(get("/signup/social")).getResponse().getContentAsString())
                .doesNotContain("이 이메일로 가입한 계정이 이미 있어요");

        members.localMember("hasemail", "이메일있음", "nover@x.com", "Blog#2026ok", true);
        Browser github = new Browser(mockMvc);
        github.perform(socialLogin("GITHUB", "777", null, "octo"));
        assertThat(github.perform(get("/signup/social")).getResponse().getContentAsString())
                .doesNotContain("이 이메일로 가입한 계정이 이미 있어요");
    }

    @Test
    void pendingInformationExpiresAfterTenMinutes() throws Exception {
        Browser browser = pendingGoogle("g-7", "late@gmail.com", "Late");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(browser.perform(completeSocial("late", "늦었어요")).getResponse().getHeader("Location"))
                .isEqualTo("/login?social");
        assertThat(browser.perform(get("/login?social")).getResponse().getContentAsString()).contains("다시 소셜 로그인해 주세요");
        assertThat(memberCount()).isZero();

        Browser other = pendingGoogle("g-8", "late2@gmail.com", "Late Two");
        clock.advance(Duration.ofMinutes(11));
        assertThat(other.perform(get("/signup/social")).getResponse().getHeader("Location")).isEqualTo("/login?social");
    }

    @Test
    void githubWithoutVerifiedEmailAsksForEmailAndSendsVerification() throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.perform(socialLogin("GITHUB", "4242", null, "octocat"));
        String page = browser.perform(get("/signup/social")).getResponse().getContentAsString();
        assertThat(page).contains("id=\"email\"").contains("<strong>gi-</strong>");

        MvcResult missing = browser.perform(completeSocial("octocat", "옥토캣"));
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(missing.getResponse().getContentAsString()).contains("올바른 이메일 주소를 입력해 주세요");

        MvcResult done = browser.perform(completeSocial("octocat", "옥토캣").param("email", " Octo@X.com "));
        assertThat(done.getResponse().getHeader("Location")).isEqualTo("/");
        Map<String, Object> row = jdbc.queryForMap("SELECT a.provider, a.provider_user_id, a.email, a.email_verified_at, m.handle "
                + "FROM auth_identity a JOIN member m ON m.id = a.member_id");
        assertThat(row).containsEntry("provider", "GITHUB").containsEntry("provider_user_id", "4242")
                .containsEntry("email", "octo@x.com").containsEntry("email_verified_at", null).containsEntry("handle", "gi-octocat");
        List<MailpitClient.Mail> mails = mailpit.awaitMessagesTo("octo@x.com", 1);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).token("/auth/verify")).isPresent();
    }
}
