package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.PendingSocialSignup;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 로그인 판정(FR-020~FR-023, FR-033, contracts/account-service.md §2). */
@Service
public class SocialLoginService {

    /** 판정 결과. */
    public sealed interface Resolution permits ExistingMember, NewSignupRequired {
    }

    public record ExistingMember(long memberId, String role) implements Resolution {
    }

    public record NewSignupRequired(PendingSocialSignup pending) implements Resolution {
    }

    private final AuthIdentityRepository authIdentityRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public SocialLoginService(AuthIdentityRepository authIdentityRepository, MemberRepository memberRepository,
                              Clock clock) {
        this.authIdentityRepository = authIdentityRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /** {@code (provider, provider_user_id)} 계정이 있으면 그 회원, 없으면 가입 대기 정보(계정은 만들지 않음). */
    @Transactional(readOnly = true)
    public Resolution resolve(SocialProfile profile) {
        Optional<AuthIdentity> identity = authIdentityRepository.findByProviderAndProviderUserId(
                profile.provider(), profile.providerUserId());
        if (identity.isPresent()) {
            long memberId = identity.get().getMemberId();
            String role = memberRepository.findById(memberId).map(m -> m.getRole().name()).orElse("USER");
            return new ExistingMember(memberId, role);
        }
        return new NewSignupRequired(PendingSocialSignup.from(profile, clock.instant()));
    }

    /**
     * FR-033: 인증된 이메일과 같은 이메일을 가진 다른 수단의 계정(수단 이름만). 인증된 이메일이 없으면 빈 목록,
     * 탈퇴 유예·익명 처리 계정은 제외.
     */
    @Transactional(readOnly = true)
    public List<Provider> findSameEmailAccounts(PendingSocialSignup pending) {
        if (!pending.hasVerifiedEmail()) {
            return List.of();
        }
        return authIdentityRepository.findByEmailAndProviderNot(pending.verifiedEmail(), pending.provider()).stream()
                .map(AuthIdentity::getProvider)
                .distinct()
                .toList();
    }
}
