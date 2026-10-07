package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.HandleService;
import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.account.domain.HandleViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 블로그 주소 접속(US4, FR-012, FR-013). */
class BlogAddressRoutingIT extends IntegrationTestBase {

    @Autowired
    HandleService handleService;

    @Autowired
    NicknamePolicy nicknamePolicy;

    @Test
    void uppercaseHandleRedirectsPermanentlyBeforeExistenceCheck() throws Exception {
        mockMvc.perform(get("/@Kim755030"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/@kim755030"));
        mockMvc.perform(get("/@Kim755030/posts/12").queryParam("x", "1"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/@kim755030/posts/12?x=1"));
    }

    @Test
    void activeMemberBlogShowsByline() throws Exception {
        members.active("kim755030", "김민서");
        mockMvc.perform(get("/@kim755030"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("김민서")))
                .andExpect(content().string(Matchers.containsString("href=\"/@kim755030\"")));
    }

    @Test
    void unknownInvalidAndWithdrawnHandlesAreTheSame404() throws Exception {
        members.withdrawing("kim755030", "김민서", Instant.now());
        members.anonymized("lee755030", Instant.now().minusSeconds(3600), Instant.now());
        String reference = body404("/no-such-page");
        for (String path : new String[] {"/@nobody123", "/@없는주소", "/@kim755030", "/@lee755030"}) {
            assertThat(body404(path)).as(path).isEqualTo(reference);
        }
    }

    @Test
    void anonymizedHandleStaysTakenButNicknameIsFree() {
        members.anonymized("kim755030", Instant.now().minusSeconds(3600), Instant.now());
        assertThatThrownBy(() -> handleService.validateForSignup("kim755030", Provider.LOCAL))
                .isInstanceOfSatisfying(HandleViolationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(HandleViolation.HANDLE_DUPLICATE);
                    assertThat(e.getSuggestion()).isEqualTo("kim755030_2");
                });
        // 익명 처리 전 닉네임이 무엇이었든 NULL이 되어 풀린다
        assertThat(nicknamePolicy.check("김민서", null).available()).isTrue();
    }

    @Test
    void withdrawingMemberHandleIsTakenToo() {
        members.withdrawing("kim755030", "김민서", Instant.now());
        assertThatThrownBy(() -> handleService.validateForSignup("kim755030", Provider.LOCAL))
                .isInstanceOf(HandleViolationException.class);
        assertThat(nicknamePolicy.check("김민서", null).available()).isFalse();
    }

    private String body404(String path) throws Exception {
        String html = mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        // CSRF 토큰 값은 요청마다 다르므로 비교에서 뺀다
        return html.replaceAll("<meta name=\"_csrf\" content=\"[^\"]*\">", "");
    }
}
