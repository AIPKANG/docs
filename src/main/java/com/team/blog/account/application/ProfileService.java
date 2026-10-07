package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.NicknameRules;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.LoginRequiredException;
import com.team.blog.shared.error.NicknameChangeTooSoonException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로필 저장(C-AUTH-2, 11 §5, research R-2). 대상은 로그인 정보로만 정한다(헌법 III, FR-002).
 *
 * <ol>
 *   <li>보낸 칸을 모두 검사해 실패를 모은다(쓰기 없음). 하나라도 있으면 {@link ProfileValidationException}.</li>
 *   <li>한 트랜잭션: 회원 행 잠금 → 002 {@link NicknameChangeService#change}(같은 트랜잭션, 제한·규칙 재검사) → 소개 →
 *       프로필 이미지 연결·해제. 잠금 때문에 동시 저장은 직렬화되고 나중에 커밋한 쪽이 남는다(FR-007).</li>
 *   <li>{@code uq_member_nickname} 경합은 트랜잭션 밖에서 번역한다(002 research R-7).</li>
 * </ol>
 */
@Service
public class ProfileService {

    private final AccountGuard accountGuard;
    private final MemberRepository memberRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final NicknamePolicy nicknamePolicy;
    private final NicknameChangeService nicknameChangeService;
    private final BioPolicy bioPolicy;
    private final MemberUniqueViolationTranslator translator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ProfileService(AccountGuard accountGuard, MemberRepository memberRepository,
                          AuthIdentityRepository authIdentityRepository, NicknamePolicy nicknamePolicy,
                          NicknameChangeService nicknameChangeService, BioPolicy bioPolicy,
                          MemberUniqueViolationTranslator translator, TransactionTemplate transactionTemplate,
                          Clock clock) {
        this.accountGuard = accountGuard;
        this.memberRepository = memberRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.nicknamePolicy = nicknamePolicy;
        this.nicknameChangeService = nicknameChangeService;
        this.bioPolicy = bioPolicy;
        this.translator = translator;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileView view(Optional<CurrentUser> currentUser) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        return view(user.memberId());
    }

    /**
     * @throws ProfileValidationException 실패 칸 모음(아무것도 바뀌지 않음)
     * @throws LoginRequiredException     비회원
     */
    public ProfileView update(Optional<CurrentUser> currentUser, ProfileUpdateCommand command) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        long memberId = user.memberId();
        Member current = memberRepository.findById(memberId).orElseThrow(LoginRequiredException::new);

        List<FieldError> errors = new ArrayList<>();
        command.invalidFields().forEach(field -> errors.add(FieldError.of(field, "INVALID_VALUE")));
        boolean nicknameChange = precheckNickname(current, command, errors);
        String bio = precheckBio(command, errors);
        if (!errors.isEmpty()) {
            throw new ProfileValidationException(errors);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> write(memberId, command, nicknameChange, bio));
        } catch (NicknameChangeTooSoonException e) {
            throw new ProfileValidationException(FieldError.nicknameTooSoon(e.getNextAllowedAt()));
        } catch (NicknameViolationException e) {
            throw new ProfileValidationException(nicknameError(e));
        } catch (DataIntegrityViolationException e) {
            try {
                translator.translate(e);
            } catch (NicknameViolationException v) {
                throw new ProfileValidationException(nicknameError(v));
            }
        }
        return transactionTemplate.execute(status -> view(memberId));
    }

    // ----- 사전 검사(쓰기 없음) -----

    /** @return 실제 닉네임 변경이 있는지 */
    private boolean precheckNickname(Member current, ProfileUpdateCommand command, List<FieldError> errors) {
        if (!command.nickname().present() || command.nickname().value() == null
                || command.invalidFields().contains("nickname")) {
            return false;
        }
        String raw = command.nickname().value();
        if (NicknameRules.normalize(raw).equals(current.getNickname())) {
            return false;
        }
        Optional<Instant> next = nicknameChangeService.nextAllowedAt(current.getId());
        if (next.isPresent()) {
            errors.add(FieldError.nicknameTooSoon(next.get()));
            return true;
        }
        NicknameCheckResult check = nicknamePolicy.check(raw, current.getId());
        if (check.violation() != null) {
            errors.add(FieldError.of("nickname", check.violation().name()));
        }
        return true;
    }

    /** @return 저장할 정리값(보내지 않았으면 null) */
    private String precheckBio(ProfileUpdateCommand command, List<FieldError> errors) {
        if (!command.bio().present() || command.invalidFields().contains("bio")) {
            return null;
        }
        BioPolicy.BioCheckResult check = bioPolicy.check(command.bio().value());
        if (!check.valid()) {
            errors.add(FieldError.of("bio", check.violation().name()));
        }
        return check.normalized();
    }

    // ----- 쓰기(한 트랜잭션) -----

    private void write(long memberId, ProfileUpdateCommand command, boolean nicknameChange, String bio) {
        Instant now = clock.instant();
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(LoginRequiredException::new);
        if (nicknameChange) {
            nicknameChangeService.change(memberId, command.nickname().value());
        }
        if (command.bio().present()) {
            member.changeBio(bio, now);
        }
        memberRepository.saveAndFlush(member);
    }

    private ProfileView view(long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(LoginRequiredException::new);
        Optional<AuthIdentity> identity = authIdentityRepository.findByMemberId(memberId);
        return new ProfileView(member.getHandle(), member.getNickname(), member.getBio(), member.getProfileImageId(),
                member.getProfileImageUrl(), identity.map(AuthIdentity::getEmail).orElse(null),
                identity.map(a -> a.getProvider().name()).orElse(null),
                identity.map(AuthIdentity::isVerified).orElse(false), member.getDefaultVisibility(),
                nicknameChangeService.nextAllowedAt(memberId).orElse(null));
    }

    private static FieldError nicknameError(NicknameViolationException e) {
        return e.isConcurrent() ? FieldError.nicknameTakenConcurrently() : FieldError.of("nickname", e.getCode().name());
    }
}
