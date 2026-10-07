package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.NicknameChangeResult;
import com.team.blog.account.application.NicknameChangeService;
import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.shared.error.NicknameChangeTooSoonException;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 닉네임 변경 30일 제한(US3, SC-007). */
class NicknameChangeIT extends IntegrationTestBase {

    @Autowired
    NicknameChangeService nicknameChangeService;

    @Autowired
    NicknamePolicy nicknamePolicy;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void firstChangeAfterSignupRecordsClockTime() {
        long id = members.active("kim755030", "김민서");
        Instant now = clock.instant();
        NicknameChangeResult result = nicknameChangeService.change(id, "민서");
        assertThat(result).isEqualTo(new NicknameChangeResult.Changed(now));
        assertThat(nickname(id)).isEqualTo("민서");
        assertThat(changedAt(id)).isEqualTo(now);
        assertThat(nicknameChangeService.nextAllowedAt(id)).contains(now.plus(Duration.ofDays(30)));
    }

    @Test
    void secondChangeWithin30DaysIsRejected() {
        long id = members.active("kim755030", "김민서");
        Instant first = clock.instant();
        nicknameChangeService.change(id, "민서");
        clock.advance(Duration.ofDays(29));
        assertThatThrownBy(() -> nicknameChangeService.change(id, "민서킴"))
                .isInstanceOfSatisfying(NicknameChangeTooSoonException.class,
                        e -> assertThat(e.getNextAllowedAt()).isEqualTo(first.plus(Duration.ofDays(30))));
        assertThat(nickname(id)).isEqualTo("민서");
    }

    @Test
    void savingSameValueIsUnchangedEvenDuringCooldown() {
        long id = members.active("kim755030", "김민서");
        assertThat(nicknameChangeService.change(id, "김민서")).isEqualTo(NicknameChangeResult.UNCHANGED);
        assertThat(changedAt(id)).isNull();
        nicknameChangeService.change(id, "민서");
        Instant changed = changedAt(id);
        clock.advance(Duration.ofDays(1));
        assertThat(nicknameChangeService.change(id, "  민서 ")).isEqualTo(NicknameChangeResult.UNCHANGED);
        assertThat(changedAt(id)).isEqualTo(changed);
    }

    @Test
    void oldNicknameIsFreeImmediately() {
        long id = members.active("kim755030", "김민서");
        nicknameChangeService.change(id, "민서");
        assertThat(nicknamePolicy.validate("김민서", null).value()).isEqualTo("김민서");
        members.active("lee755030", "김민서");
    }

    @Test
    void after30DaysChangeSucceedsAndCaseOnlyChangeCounts() {
        long id = members.active("kim755030", "김민서");
        nicknameChangeService.change(id, "kim");
        clock.advance(Duration.ofDays(30));
        assertThat(nicknameChangeService.nextAllowedAt(id)).isEmpty();
        Instant now = clock.instant();
        assertThat(nicknameChangeService.change(id, "Kim")).isEqualTo(new NicknameChangeResult.Changed(now));
        assertThat(nickname(id)).isEqualTo("Kim");
        assertThat(nicknameChangeService.nextAllowedAt(id)).contains(now.plus(Duration.ofDays(30)));
    }

    @Test
    void exceptionRollsBackTheCallersWholeTransaction() {
        long id = members.active("kim755030", "김민서");
        nicknameChangeService.change(id, "민서");
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.update("UPDATE member SET bio = '새 소개' WHERE id = ?", id);
            nicknameChangeService.change(id, "다른이름");
        })).isInstanceOf(NicknameChangeTooSoonException.class);
        assertThat(jdbc.queryForObject("SELECT bio FROM member WHERE id = ?", String.class, id)).isNull();
    }

    private String nickname(long id) {
        return jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, id);
    }

    private Instant changedAt(long id) {
        Timestamp ts = jdbc.queryForObject("SELECT nickname_changed_at FROM member WHERE id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }
}
