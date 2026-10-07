package com.team.blog.account.application;

import com.team.blog.account.application.MemberUniqueViolationTranslator.SignupContext;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.PendingSocialSignup;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.RedisTokenStore;
import com.team.blog.account.infra.TokenKind;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.event.MemberSignedUp;
import com.team.blog.shared.event.VerificationMailRequested;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소셜 가입 마무리(US3, research R-7·R-8). 바깥 메서드(트랜잭션 없음)가 10분 만료를 보고, 안쪽 트랜잭션에서 계정을 만든다.
 * 동시 완료로 DB 제약에 지면 트랜잭션 밖에서 번역해 이미 생긴 계정을 돌려준다(그 계정으로 로그인).
 */
@Service
public class SocialSignupService {

    /** @param created 이번 요청이 계정을 만들었으면 true, 이미 있던 계정이면 false */
    public record Result(long memberId, boolean created) {
    }

    private final MemberRepository memberRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AgreementRecorder agreementRecorder;
    private final HandleService handleService;
    private final NicknamePolicy nicknamePolicy;
    private final RedisTokenStore tokenStore;
    private final MemberUniqueViolationTranslator translator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final AuthProperties properties;
    private final Clock clock;

    public SocialSignupService(MemberRepository memberRepository, AuthIdentityRepository authIdentityRepository,
                               AgreementRecorder agreementRecorder, HandleService handleService,
                               NicknamePolicy nicknamePolicy, RedisTokenStore tokenStore,
                               MemberUniqueViolationTranslator translator, ApplicationEventPublisher events,
                               TransactionTemplate transactionTemplate, AuthProperties properties, Clock clock) {
        this.memberRepository = memberRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.agreementRecorder = agreementRecorder;
        this.handleService = handleService;
        this.nicknamePolicy = nicknamePolicy;
        this.tokenStore = tokenStore;
        this.translator = translator;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @throws PendingExpiredException 대기 정보 없음·10분 경과
     * @throws AgreementRequiredException, InvalidEmailException, 002 주소·닉네임 예외
     */
    public Result complete(PendingSocialSignup pending, SocialSignupCommand command) {
        if (pending == null || pending.isExpired(clock.instant(), properties.pendingSocialTtl())) {
            throw new PendingExpiredException();
        }
        Objects.requireNonNull(command);
        Optional<AuthIdentity> already = authIdentityRepository.findByProviderAndProviderUserId(
                pending.provider(), pending.providerUserId());
        if (already.isPresent()) {
            return new Result(already.get().getMemberId(), false);
        }
        Handle[] submittedHandle = new Handle[1];
        try {
            long memberId = Objects.requireNonNull(
                    transactionTemplate.execute(status -> doComplete(pending, command, submittedHandle)));
            return new Result(memberId, true);
        } catch (DataIntegrityViolationException e) {
            ExistingSocialAccount existing = translator.translate(e,
                    SignupContext.social(pending.provider(), pending.providerUserId(), submittedHandle[0]));
            return new Result(existing.memberId(), false);
        } catch (HandleViolationException | NicknameViolationException e) {
            // 같은 제출이 조금 먼저 커밋되면 사전 검사에서 "이미 사용 중"으로 걸린다 → 그 소셜 계정이 생겼으면 그 계정으로
            Optional<AuthIdentity> created = authIdentityRepository.findByProviderAndProviderUserId(
                    pending.provider(), pending.providerUserId());
            if (created.isPresent()) {
                return new Result(created.get().getMemberId(), false);
            }
            throw e;
        }
    }

    private long doComplete(PendingSocialSignup pending, SocialSignupCommand command, Handle[] submittedHandle) {
        agreementRecorder.requireBoth(command.agreeTerms(), command.agreePrivacy());
        String email;
        boolean verified;
        if (pending.hasVerifiedEmail()) {
            email = pending.verifiedEmail();
            verified = true;
        } else {
            email = EmailRules.normalizeAndValidate(command.email());
            verified = false;
        }
        Handle handle = handleService.validateForSignup(command.handle(), pending.provider());
        submittedHandle[0] = handle;
        Nickname nickname = nicknamePolicy.validate(command.nickname(), null);

        Instant now = clock.instant();
        Member member = memberRepository.saveAndFlush(new Member(handle, nickname, now));
        long memberId = member.getId();
        authIdentityRepository.saveAndFlush(AuthIdentity.social(memberId, pending.provider(), pending.providerUserId(),
                email, verified ? now : null, now));
        agreementRecorder.recordSignupAgreements(memberId, now);
        events.publishEvent(new MemberSignedUp(memberId, pending.provider().name()));
        if (!verified) {
            String rawToken = tokenStore.issue(TokenKind.VERIFY, memberId);
            events.publishEvent(new VerificationMailRequested(memberId, email, rawToken));
        }
        return memberId;
    }
}
