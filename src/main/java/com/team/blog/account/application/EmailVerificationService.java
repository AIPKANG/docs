package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.account.infra.RedisTokenStore;
import com.team.blog.account.infra.TokenKind;
import com.team.blog.shared.event.EmailVerified;
import com.team.blog.shared.event.VerificationMailRequested;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 인증·재발송(FR-008~FR-010, contracts/account-service.md §2). */
@Service
public class EmailVerificationService {

    public enum VerifyResult { VERIFIED, EXPIRED_OR_USED }

    public enum ResendResult { SENT, RATE_LIMITED, ALREADY_VERIFIED }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AuthIdentityRepository authIdentityRepository;
    private final RedisTokenStore tokenStore;
    private final RedisRateLimiter rateLimiter;
    private final AuthProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public EmailVerificationService(AuthIdentityRepository authIdentityRepository, RedisTokenStore tokenStore,
                                    RedisRateLimiter rateLimiter, AuthProperties properties,
                                    ApplicationEventPublisher events, Clock clock) {
        this.authIdentityRepository = authIdentityRepository;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** 링크 사용: 토큰을 {@code GETDEL}로 한 번만 꺼내 {@code email_verified_at}을 기록한다. */
    @Transactional
    public VerifyResult verify(String rawToken) {
        Optional<Long> memberId = tokenStore.consume(TokenKind.VERIFY, rawToken);
        if (memberId.isEmpty()) {
            return VerifyResult.EXPIRED_OR_USED;
        }
        Optional<AuthIdentity> identity = authIdentityRepository.findByMemberId(memberId.get());
        if (identity.isEmpty()) {
            return VerifyResult.EXPIRED_OR_USED;
        }
        identity.get().markVerified(clock.instant());
        events.publishEvent(new EmailVerified(memberId.get()));
        return VerifyResult.VERIFIED;
    }

    /**
     * 재발송: 회원당 1분 1회·하루 10회(키 {@code auth:verify-resend:{memberId}:min},
     * {@code auth:verify-resend:{memberId}:{yyyyMMdd}}). 새 토큰을 발급하면 이전 링크는 무효(FR-009).
     */
    @Transactional(readOnly = true)
    public ResendResult resend(long memberId) {
        Optional<AuthIdentity> identity = authIdentityRepository.findByMemberId(memberId);
        if (identity.isEmpty() || identity.get().getEmail() == null) {
            return ResendResult.RATE_LIMITED;
        }
        if (identity.get().isVerified()) {
            return ResendResult.ALREADY_VERIFIED;
        }
        String base = "auth:verify-resend:" + memberId;
        if (!rateLimiter.tryAcquire(base + ":min", properties.verifyResend().perMinute(), Duration.ofMinutes(1)).allowed()) {
            return ResendResult.RATE_LIMITED;
        }
        String day = DAY.format(clock.instant().atZone(clock.getZone()));
        if (!rateLimiter.tryAcquire(base + ":" + day, properties.verifyResend().perDay(), Duration.ofDays(1)).allowed()) {
            return ResendResult.RATE_LIMITED;
        }
        String rawToken = tokenStore.issue(TokenKind.VERIFY, memberId);
        events.publishEvent(new VerificationMailRequested(memberId, identity.get().getEmail(), rawToken));
        return ResendResult.SENT;
    }
}
