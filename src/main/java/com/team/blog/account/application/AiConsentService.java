package com.team.blog.account.application;

import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 전송 동의(021 FR-008~FR-010, 34 §7). V1 {@code member_agreement}의 {@code AI} 종류 행으로 기록한다(34 ERD 제안의
 * {@code member.ai_consent_at} 대신 — 새 컬럼 없이 같은 뜻). 철회하면 행을 지워 다음 사용 때 다시 묻는다.
 */
@Service
public class AiConsentService {

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final Clock clock;

    public AiConsentService(JdbcTemplate jdbc, AccountGuard accountGuard, Clock clock) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.clock = clock;
    }

    public boolean hasConsent(long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM member_agreement WHERE member_id = ? AND type = 'AI')", Boolean.class, memberId));
    }

    public void agree(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireWritable(current);
        jdbc.update("INSERT INTO member_agreement (member_id, type, agreed_at) VALUES (?, 'AI', ?) ON CONFLICT DO NOTHING",
                user.memberId(), Timestamp.from(clock.instant()));
    }

    public void withdraw(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        jdbc.update("DELETE FROM member_agreement WHERE member_id = ? AND type = 'AI'", user.memberId());
    }
}
