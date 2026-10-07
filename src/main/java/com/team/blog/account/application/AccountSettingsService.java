package com.team.blog.account.application;

import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.LoginRequiredException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 설정(11 §6-3, FR-028): 새 글 기본 공개 범위. 글 기능(004·005)은 {@link #defaultVisibility(long)}로 새 글의 시작 값을 읽는다.
 * 허용 값은 공통 DB CHECK와 같은 {@code PUBLIC}/{@code PRIVATE} — 친구 공개 구현자는 값을 추가한다(헌법 II).
 */
@Service
public class AccountSettingsService {

    static final Set<String> VISIBILITIES = Set.of("PUBLIC", "PRIVATE");

    private final AccountGuard accountGuard;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public AccountSettingsService(AccountGuard accountGuard, MemberRepository memberRepository, Clock clock) {
        this.accountGuard = accountGuard;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public String defaultVisibility(long memberId) {
        return memberRepository.findById(memberId).map(Member::getDefaultVisibility).orElse("PUBLIC");
    }

    /** @throws ProfileValidationException {@code INVALID_VISIBILITY} */
    @Transactional
    public String changeDefaultVisibility(Optional<CurrentUser> currentUser, String value) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        if (value == null || !VISIBILITIES.contains(value)) {
            throw new ProfileValidationException(FieldError.of("defaultVisibility", "INVALID_VISIBILITY"));
        }
        Member member = memberRepository.findByIdForUpdate(user.memberId()).orElseThrow(LoginRequiredException::new);
        member.changeDefaultVisibility(value, clock.instant());
        return value;
    }
}
