package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.account.application.HandleService;
import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.account.domain.HandleViolation;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.error.GlobalExceptionHandler;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;

/** 걸린 금칙어는 응답·예외·로그 어디에도 나오지 않는다(SC-006). */
@ExtendWith(OutputCaptureExtension.class)
class BannedWordLeakIT extends IntegrationTestBase {

    /** 테스트 목록 단어와 입력에 쓴 변형. */
    private static final List<String> WORDS = List.of("시발", "시1발", "병신", "병1신", "shit", "sh1t", "fack", "f4ck");

    @Autowired
    HandleService handleService;

    @Autowired
    NicknamePolicy nicknamePolicy;

    @Autowired
    GlobalExceptionHandler globalExceptionHandler;

    @Test
    void bannedWordsNeverLeak(CapturedOutput output) throws Exception {
        List<String> observed = new ArrayList<>();

        for (String handle : List.of("sh1tkim", "f4ck_blog", "kim_sh_it")) {
            observed.add(mockMvc.perform(get("/api/handles/availability").param("handle", handle))
                    .andReturn().getResponse().getContentAsString());
            try {
                handleService.validateForSignup(handle, Provider.LOCAL);
            } catch (HandleViolationException e) {
                assertThat(e.getCode()).isEqualTo(HandleViolation.HANDLE_BANNED_WORD);
                observed.add(e.getMessage());
                observed.add(e.toString());
                ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleViolation(e);
                observed.add(String.valueOf(response.getBody()));
            }
        }
        for (String nickname : List.of("시1발왕", "병1신", "sh1tkim")) {
            observed.add(mockMvc.perform(get("/api/nicknames/availability").param("nickname", nickname))
                    .andReturn().getResponse().getContentAsString());
            try {
                nicknamePolicy.validate(nickname, null);
            } catch (NicknameViolationException e) {
                assertThat(e.getCode()).isEqualTo(NicknameViolation.NICKNAME_BANNED_WORD);
                observed.add(e.getMessage());
                observed.add(e.toString());
                observed.add(String.valueOf(globalExceptionHandler.nicknameViolation(e).getBody()));
            }
        }

        assertThat(observed).hasSizeGreaterThan(10);
        for (String text : observed) {
            for (String word : WORDS) {
                assertThat(text.toLowerCase()).as(text).doesNotContain(word);
            }
        }
        String logs = output.getAll().toLowerCase();
        assertThat(logs).contains("banned word matched");
        for (String word : WORDS) {
            assertThat(logs).doesNotContain(word);
        }
    }
}
