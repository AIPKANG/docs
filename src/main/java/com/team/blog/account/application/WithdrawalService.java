package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.LoginAttemptStore;
import com.team.blog.shared.error.AdminCannotWithdrawException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.event.MemberRestored;
import com.team.blog.shared.event.MemberWithdrawalRequested;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 신청·복구(023 FR-001~FR-025, 13 §3). 신청: 로그인(인증 전 허용) → 관리자면 409 → 확인 체크 → 본인 확인(이메일 가입은
 * 비밀번호, 소셜은 "탈퇴" 입력, 5번 틀리면 15분 잠금) → 상태 "탈퇴 신청"·시각 기록 → 커밋 뒤 세션 끊기·접수 메일. 사유는 묻지 않는다.
 * 복구: 복구 전용 세션에서 [복구하기]를 눌렀을 때만 상태를 되돌린다(로그인만으로는 복구되지 않음).
 */
@Service
public class WithdrawalService {

    public static final String SOCIAL_PHRASE = "탈퇴";

    public record Overview(String handle, long postCount, long commentCount, long likesReceived, Instant restoreDeadline,
                           boolean passwordAccount) {
    }

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final AuthIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptStore attemptStore;
    private final WithdrawalProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WithdrawalService(JdbcTemplate jdbc, AccountGuard accountGuard, AuthIdentityRepository identities,
                             PasswordEncoder passwordEncoder, LoginAttemptStore attemptStore, WithdrawalProperties properties,
                             ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.attemptStore = attemptStore;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** 탈퇴 화면 안내(FR-003): 화면을 열 때 계산. */
    public Overview overview(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long id = user.memberId();
        return jdbc.queryForObject("""
                SELECT m.handle,
                       (SELECT count(*) FROM post p WHERE p.author_id = m.id) AS posts,
                       (SELECT count(*) FROM comment c JOIN post p ON p.id = c.post_id
                         WHERE c.author_id = m.id AND p.author_id <> m.id AND c.deleted_at IS NULL) AS comments,
                       (SELECT COALESCE(sum(p.like_count), 0) FROM post p WHERE p.author_id = m.id) AS likes
                FROM member m WHERE m.id = ?
                """, (rs, n) -> new Overview(rs.getString("handle"), rs.getLong("posts"), rs.getLong("comments"),
                rs.getLong("likes"), clock.instant().plus(properties.grace()), passwordAccount(id)), id);
    }

    private boolean passwordAccount(long memberId) {
        return identities.findByMemberId(memberId).map(i -> i.getProvider() == Provider.LOCAL).orElse(false);
    }

    @Transactional
    public Instant request(Optional<CurrentUser> current, boolean confirmed, String verification) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        long memberId = user.memberId();
        if ("ADMIN".equals(user.role())) {
            throw new AdminCannotWithdrawException();
        }
        if (!confirmed) {
            throw new ProfileValidationException(FieldError.of("confirm", "WITHDRAW_CONFIRM_REQUIRED"));
        }
        Optional<AuthIdentity> identity = identities.findByMemberId(memberId);
        String failKey = "withdraw:fail:" + memberId;
        String lockKey = "withdraw:lock:" + memberId;
        if (attemptStore.isLockedKey(lockKey)) {
            throw new RateLimitedException(attemptStore.remainingSeconds(lockKey));
        }
        String input = verification == null ? "" : verification;
        boolean ok = identity.isPresent() && identity.get().getProvider() == Provider.LOCAL
                ? passwordEncoder.matches(input, identity.get().getPasswordHash())
                : SOCIAL_PHRASE.equals(input.strip());
        if (!ok) {
            attemptStore.recordFailureKeys(failKey, lockKey, properties.maxFailures(), properties.lockDuration());
            throw new ProfileValidationException(FieldError.of("verification", "WITHDRAW_VERIFICATION_FAILED"));
        }
        attemptStore.clearKey(failKey);
        Instant now = clock.instant();
        int updated = jdbc.update("""
                UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ?, updated_at = ? WHERE id = ? AND withdrawn_at IS NULL
                """, Timestamp.from(now), Timestamp.from(now), memberId);
        Instant deadline = now.plus(properties.grace());
        if (updated == 1) {
            events.publishEvent(new MemberWithdrawalRequested(memberId, identity.map(AuthIdentity::getEmail).orElse(null), deadline));
        }
        return deadline;
    }

    /** 복구 화면(FR-019): 복구 기한과 남은 일수. */
    public Optional<Instant> restoreDeadline(long memberId) {
        return jdbc.query("SELECT withdrawn_at FROM member WHERE id = ? AND status = 'WITHDRAWN' AND deleted_at IS NULL",
                        (rs, n) -> rs.getTimestamp(1).toInstant(), memberId).stream().findFirst()
                .map(at -> at.plus(properties.grace()));
    }

    /** [복구하기](FR-021·FR-022): 상태·신청 시각만 되돌리면 글·댓글·반응이 그대로 다시 보인다. */
    @Transactional
    public boolean restore(long memberId) {
        int updated = jdbc.update("""
                UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL, updated_at = ?
                WHERE id = ? AND status = 'WITHDRAWN' AND deleted_at IS NULL
                """, Timestamp.from(clock.instant()), memberId);
        if (updated == 1) {
            events.publishEvent(new MemberRestored(memberId, identities.findByMemberId(memberId).map(AuthIdentity::getEmail).orElse(null)));
        }
        return updated == 1;
    }
}
