package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.PasswordPolicy;
import com.team.blog.account.domain.PasswordPolicyViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.LoginAttemptStore;
import com.team.blog.shared.error.CurrentPasswordMismatchException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.LoginRequiredException;
import com.team.blog.shared.error.PasswordChangeLockedException;
import com.team.blog.shared.error.PasswordNotSupportedException;
import com.team.blog.shared.error.PasswordSameAsCurrentException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.event.PasswordChanged;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경(11 §6-2, FR-024~FR-027, 003 research R-13). 이메일 가입 계정만.
 *
 * <p>순서: LOCAL 아님 → 잠금 → 현재 비밀번호(BCrypt) 불일치(+1, 5회째 15분 잠금) → 새 = 현재 → 001 {@link PasswordPolicy}·확인.
 * 성공하면 해시를 바꾸고 {@link PasswordChanged}를 발행한다 — 커밋 후 다른 기기 세션 삭제·알림 메일
 * ({@link PasswordChangedListener}). 지금 기기의 세션 ID 재발급은 호출자(표현 계층)가 한다.
 */
@Service
public class PasswordChangeService {

    private final AccountGuard accountGuard;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptStore attemptStore;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;

    public PasswordChangeService(AccountGuard accountGuard, AuthIdentityRepository authIdentityRepository,
                                 PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                 LoginAttemptStore attemptStore, ApplicationEventPublisher events,
                                 AuthProperties properties) {
        this.accountGuard = accountGuard;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.attemptStore = attemptStore;
        this.events = events;
        this.properties = properties;
    }

    /**
     * @param currentSessionId 남길 세션(지금 기기)
     * @throws PasswordNotSupportedException      소셜 계정
     * @throws PasswordChangeLockedException      잠금 중
     * @throws CurrentPasswordMismatchException   현재 비밀번호 틀림
     * @throws PasswordSameAsCurrentException     새 = 현재
     * @throws ProfileValidationException         정책 위반({@code newPassword})·확인 불일치({@code newPasswordConfirm})
     */
    @Transactional
    public void change(Optional<CurrentUser> currentUser, PasswordChangeCommand command, String currentSessionId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        long memberId = user.memberId();
        AuthIdentity identity = authIdentityRepository.findByMemberId(memberId).orElseThrow(LoginRequiredException::new);
        if (identity.getProvider() != Provider.LOCAL) {
            throw new PasswordNotSupportedException();
        }
        String failKey = LoginAttemptStore.passwordChangeFailKey(memberId);
        String lockKey = LoginAttemptStore.passwordChangeLockKey(memberId);
        if (attemptStore.isLockedKey(lockKey)) {
            throw new PasswordChangeLockedException(attemptStore.remainingSeconds(lockKey));
        }
        String current = Objects.requireNonNullElse(command.currentPassword(), "");
        if (!passwordEncoder.matches(current, identity.getPasswordHash())) {
            AuthProperties.PasswordChange limits = properties.passwordChange();
            attemptStore.recordFailureKeys(failKey, lockKey, limits.maxFailures(), limits.lockDuration());
            throw new CurrentPasswordMismatchException();
        }
        String next = Objects.requireNonNullElse(command.newPassword(), "");
        if (next.equals(current)) {
            throw new PasswordSameAsCurrentException();
        }
        List<PasswordPolicyViolation> violations = passwordPolicy.validate(next, identity.getEmail());
        if (!violations.isEmpty()) {
            throw new ProfileValidationException(FieldError.password("newPassword", violations.getFirst().name()));
        }
        if (!next.equals(command.newPasswordConfirm())) {
            throw new ProfileValidationException(FieldError.of("newPasswordConfirm", "PASSWORD_MISMATCH"));
        }
        identity.changePasswordHash(passwordEncoder.encode(next));
        authIdentityRepository.save(identity);
        attemptStore.clearKey(failKey);
        events.publishEvent(new PasswordChanged(memberId, identity.getEmail(), currentSessionId));
    }
}
