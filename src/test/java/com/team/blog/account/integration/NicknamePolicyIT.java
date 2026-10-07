package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.support.IntegrationTestBase;
import java.text.Normalizer;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NicknamePolicyIT extends IntegrationTestBase {

    @Autowired
    NicknamePolicy nicknamePolicy;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void duplicateIgnoresCase() {
        members.active("kim755030", "Kim");
        assertViolation("kim", null, NicknameViolation.NICKNAME_DUPLICATE);
        assertViolation("KIM", null, NicknameViolation.NICKNAME_DUPLICATE);
        assertThat(nicknamePolicy.validate("KIM2", null).value()).isEqualTo("KIM2");
    }

    @Test
    void excludesSelf() {
        long me = members.active("kim755030", "Kim");
        assertThat(nicknamePolicy.validate("kim", me).value()).isEqualTo("kim");
        long other = members.active("lee", "Lee");
        assertViolation("kim", other, NicknameViolation.NICKNAME_DUPLICATE);
    }

    @Test
    void storesNfc() {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        Nickname nickname = nicknamePolicy.validate(nfd, null);
        memberRepository.saveAndFlush(new Member(Handle.parse("kim755030"), nickname, Instant.now()));
        assertThat(jdbc.queryForObject("SELECT length(nickname) FROM member WHERE handle = 'kim755030'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void socialNameSuggestion() {
        assertThat(nicknamePolicy.suggestFromSocialName("Kim Min-seo")).contains("KimMinseo");
        assertThat(nicknamePolicy.suggestFromSocialName("김민서 (Minseo)")).contains("김민서Minseo");
        assertThat(nicknamePolicy.suggestFromSocialName("Christopher Columbus")).contains("Christophe");
        assertThat(nicknamePolicy.suggestFromSocialName("A")).isEmpty();
        members.active("kim755030", "Kim");
        assertThat(nicknamePolicy.suggestFromSocialName("Kim")).isEmpty();
    }

    @Test
    void withdrawingMemberKeepsNicknameUntilAnonymized() {
        members.withdrawing("kim755030", "Kim", Instant.now());
        assertViolation("kim", null, NicknameViolation.NICKNAME_DUPLICATE);
        members.anonymized("lee755030", Instant.now().minusSeconds(3600), Instant.now());
        jdbc.update("DELETE FROM member WHERE handle = 'kim755030'");
        members.anonymized("kim755030", Instant.now().minusSeconds(3600), Instant.now());
        assertThat(nicknamePolicy.validate("Kim", null).value()).isEqualTo("Kim");
    }

    @Test
    void bannedWordNeverAppearsInTheException() {
        assertThatThrownBy(() -> nicknamePolicy.validate("시1발왕", null))
                .isInstanceOfSatisfying(NicknameViolationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(NicknameViolation.NICKNAME_BANNED_WORD);
                    assertThat(e.getMessage()).doesNotContain("시발").doesNotContain("시1발");
                    assertThat(e.toString()).doesNotContain("시발").doesNotContain("시1발");
                });
    }

    private void assertViolation(String raw, Long exclude, NicknameViolation code) {
        assertThatThrownBy(() -> nicknamePolicy.validate(raw, exclude))
                .as(raw)
                .isInstanceOfSatisfying(NicknameViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
