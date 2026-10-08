package com.team.blog.account.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.team.blog.account.application.WithdrawalPurgeJob;
import com.team.blog.media.application.ImagePurgeStep;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 023 FR-027·SC-007: 한 단계라도 실패하면 그 회원의 변경 전부 취소, 다음 실행에서 다시. */
class WithdrawalPurgeFailureIT extends IntegrationTestBase {

    @MockitoSpyBean
    ImagePurgeStep imageStep;

    @Autowired
    WithdrawalPurgeJob purgeJob;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void failingStepRollsBackThatMemberOnly() {
        Instant t = Instant.parse("2026-10-01T00:00:00Z");
        clock.set(t.plus(Duration.ofDays(31)));
        long me = members.localMember("wfail", "wfail", "wfail@example.com", "Blog#2026ok", true);
        long ok = members.localMember("wfine", "wfine", "wfine@example.com", "Blog#2026ok", true);
        long post = posts.published(me, "남아야 할 글", "본문", 1, t);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id IN (?, ?)", Timestamp.from(t), me, ok);
        doThrow(new IllegalStateException("boom")).when(imageStep).purge(me);
        assertThat(purgeJob.run()).isEqualTo(1);
        assertThat(posts.exists(post)).isTrue();
        assertThat(jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, me)).isEqualTo("wfail");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_identity WHERE member_id = ?", Integer.class, me)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM member WHERE id = ?", Boolean.class, ok)).isTrue();
        doCallRealMethod().when(imageStep).purge(anyLong());
        assertThat(purgeJob.run()).isEqualTo(1);
        assertThat(posts.exists(post)).isFalse();
    }
}
