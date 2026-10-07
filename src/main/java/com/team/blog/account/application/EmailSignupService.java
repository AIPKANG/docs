package com.team.blog.account.application;

import com.team.blog.account.application.MemberUniqueViolationTranslator.SignupContext;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.PasswordPolicy;
import com.team.blog.account.domain.PasswordPolicyViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.RedisTokenStore;
import com.team.blog.account.infra.TokenKind;
import com.team.blog.shared.event.MemberSignedUp;
import com.team.blog.shared.event.VerificationMailRequested;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이메일 가입(US1, contracts/account-service.md §2).
 *
 * <p>바깥 {@link #signUp}은 트랜잭션이 없고 안쪽 단계를 {@link TransactionTemplate}으로 실행한다. 동시 가입으로 DB UNIQUE 제약에
 * 진 쪽은 트랜잭션 경계 밖에서 번역한다(002 contract §6): {@code uq_auth_identity} → {@link DuplicateEmailException},
 * 주소·닉네임 → {@link MemberUniqueViolationTranslator}.
 */
@Service
public class EmailSignupService {

    private final MemberRepository memberRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AgreementRecorder agreementRecorder;
    private final HandleService handleService;
    private final NicknamePolicy nicknamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RedisTokenStore tokenStore;
    private final MemberUniqueViolationTranslator translator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EmailSignupService(MemberRepository memberRepository, AuthIdentityRepository authIdentityRepository,
                              AgreementRecorder agreementRecorder, HandleService handleService,
                              NicknamePolicy nicknamePolicy, PasswordPolicy passwordPolicy,
                              PasswordEncoder passwordEncoder, RedisTokenStore tokenStore,
                              MemberUniqueViolationTranslator translator, ApplicationEventPublisher events,
                              TransactionTemplate transactionTemplate, Clock clock) {
        this.memberRepository = memberRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.agreementRecorder = agreementRecorder;
        this.handleService = handleService;
        this.nicknamePolicy = nicknamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.translator = translator;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * @return 새 회원 번호(미인증). 커밋 후 인증 메일이 나간다.
     * @throws AgreementRequiredException, InvalidEmailException, InvalidPasswordException, PasswordMismatchException,
     *         DuplicateEmailException, WithdrawnAccountExistsException, 002 주소·닉네임 예외
     */
    public long signUp(EmailSignupCommand command) {
        Objects.requireNonNull(command);
        Handle[] submittedHandle = new Handle[1];
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> doSignUp(command, submittedHandle)));
        } catch (DataIntegrityViolationException e) {
            String constraint = MemberUniqueViolationTranslator.constraintName(e);
            if (MemberUniqueViolationTranslator.UQ_AUTH_IDENTITY.equals(constraint)) {
                throw new DuplicateEmailException();
            }
            translator.translate(e, SignupContext.email(submittedHandle[0]));
            throw e; // translate는 이메일 가입 문맥에서 항상 던진다
        }
    }

    private long doSignUp(EmailSignupCommand command, Handle[] submittedHandle) {
        agreementRecorder.requireBoth(command.agreeTerms(), command.agreePrivacy());
        String email = EmailRules.normalizeAndValidate(command.email());
        List<PasswordPolicyViolation> violations = passwordPolicy.validate(command.password(), email);
        if (!violations.isEmpty()) {
            throw new InvalidPasswordException(violations);
        }
        if (!Objects.equals(command.password(), command.passwordConfirm())) {
            throw new PasswordMismatchException();
        }
        Optional<AuthIdentity> existing = authIdentityRepository.findLocalByEmail(email);
        if (existing.isPresent()) {
            boolean withdrawing = memberRepository.findById(existing.get().getMemberId())
                    .map(Member::isWithdrawalPending).orElse(false);
            throw withdrawing ? new WithdrawnAccountExistsException() : new DuplicateEmailException();
        }
        Handle handle = handleService.validateForSignup(command.handle(), Provider.LOCAL);
        submittedHandle[0] = handle;
        Nickname nickname = nicknamePolicy.validate(command.nickname(), null);

        Instant now = clock.instant();
        Member member = memberRepository.saveAndFlush(new Member(handle, nickname, now));
        long memberId = member.getId();
        authIdentityRepository.saveAndFlush(
                AuthIdentity.local(memberId, email, passwordEncoder.encode(command.password()), now));
        agreementRecorder.recordSignupAgreements(memberId, now);
        String rawToken = tokenStore.issue(TokenKind.VERIFY, memberId);
        events.publishEvent(new MemberSignedUp(memberId, Provider.LOCAL.name()));
        events.publishEvent(new VerificationMailRequested(memberId, email, rawToken));
        return memberId;
    }
}
