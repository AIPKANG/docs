package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.PasswordPolicy;
import com.team.blog.account.domain.PasswordPolicyViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.KeyHashing;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.account.infra.RedisTokenStore;
import com.team.blog.account.infra.TokenKind;
import com.team.blog.shared.event.PasswordResetCompleted;
import com.team.blog.shared.event.PasswordResetMailRequested;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기·재설정(US4, FR-016~FR-019). 요청 결과는 가입 여부·한도와 무관하게 항상 같다(SC-006).
 * 로그인 전 기능이라 정지·탈퇴 유예 회원도 쓸 수 있다(42 §9).
 */
@Service
public class PasswordResetService {

    public enum ResetResult { RESET, EXPIRED_OR_USED }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AuthIdentityRepository authIdentityRepository;
    private final MemberRepository memberRepository;
    private final RedisTokenStore tokenStore;
    private final RedisRateLimiter rateLimiter;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(AuthIdentityRepository authIdentityRepository, MemberRepository memberRepository,
                                RedisTokenStore tokenStore, RedisRateLimiter rateLimiter, PasswordPolicy passwordPolicy,
                                PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                                AuthProperties properties, Clock clock) {
        this.authIdentityRepository = authIdentityRepository;
        this.memberRepository = memberRepository;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 비밀번호 찾기 요청. 반환값이 없다 — 화면은 항상 "가입된 이메일이면 안내 메일을 보냈어요".
     * 한도(키에 원문 이메일 없음): 이메일 해시별 1분 1회·하루 10회, IP별 1시간 20회. 초과하면 아무것도 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public void request(String rawEmail, String ip) {
        AuthProperties.ResetRequest limits = properties.resetRequest();
        if (!rateLimiter.tryAcquire(RedisRateLimiter.key("auth:reset-req:ip", ip),
                limits.ipPerHour(), Duration.ofHours(1)).allowed()) {
            return;
        }
        String email;
        try {
            email = EmailRules.normalizeAndValidate(rawEmail);
        } catch (InvalidEmailException e) {
            return;
        }
        String base = "auth:reset-req:email:" + KeyHashing.emailHash(email);
        if (!rateLimiter.tryAcquire(base + ":min", limits.emailPerMinute(), Duration.ofMinutes(1)).allowed()) {
            return;
        }
        String day = DAY.format(clock.instant().atZone(clock.getZone()));
        if (!rateLimiter.tryAcquire(base + ":" + day, limits.emailPerDay(), Duration.ofDays(1)).allowed()) {
            return;
        }
        List<String> socialProviders = authIdentityRepository.findByEmailAndProviderNot(email, Provider.LOCAL).stream()
                .map(a -> a.getProvider().name()).distinct().toList();
        Optional<AuthIdentity> local = authIdentityRepository.findLocalByEmail(email)
                .filter(a -> memberRepository.findById(a.getMemberId()).map(m -> m.getDeletedAt() == null).orElse(false));
        if (local.isPresent()) {
            String rawToken = tokenStore.issue(TokenKind.RESET, local.get().getMemberId());
            events.publishEvent(new PasswordResetMailRequested(email, PasswordResetMailRequested.Kind.LOCAL_LINK,
                    rawToken, socialProviders));
        } else if (!socialProviders.isEmpty()) {
            events.publishEvent(new PasswordResetMailRequested(email, PasswordResetMailRequested.Kind.SOCIAL_ONLY,
                    null, socialProviders));
        }
    }

    /** 재설정 화면을 보여줄지(토큰을 소비하지 않음). */
    public boolean isValid(String rawToken) {
        return tokenStore.peek(TokenKind.RESET, rawToken).isPresent();
    }

    /**
     * 새 비밀번호 저장. 정책 위반이면 토큰을 소비하지 않는다. 통과하면 토큰을 원자적으로 소비({@code GETDEL})하고 해시를 바꾼 뒤
     * 커밋 후 그 회원의 모든 세션을 지운다.
     *
     * @throws InvalidPasswordException 정책 위반 @throws PasswordMismatchException 확인 불일치
     */
    @Transactional
    public ResetResult reset(String rawToken, String newPassword, String confirm) {
        Optional<Long> memberId = tokenStore.peek(TokenKind.RESET, rawToken);
        if (memberId.isEmpty()) {
            return ResetResult.EXPIRED_OR_USED;
        }
        Optional<AuthIdentity> identity = authIdentityRepository.findByMemberId(memberId.get())
                .filter(a -> a.getProvider() == Provider.LOCAL);
        if (identity.isEmpty()) {
            return ResetResult.EXPIRED_OR_USED;
        }
        List<PasswordPolicyViolation> violations = passwordPolicy.validate(newPassword, identity.get().getEmail());
        if (!violations.isEmpty()) {
            throw new InvalidPasswordException(violations);
        }
        if (!Objects.equals(newPassword, confirm)) {
            throw new PasswordMismatchException();
        }
        if (tokenStore.consume(TokenKind.RESET, rawToken).filter(memberId.get()::equals).isEmpty()) {
            return ResetResult.EXPIRED_OR_USED;
        }
        identity.get().changePasswordHash(passwordEncoder.encode(newPassword));
        events.publishEvent(new PasswordResetCompleted(memberId.get()));
        return ResetResult.RESET;
    }
}
