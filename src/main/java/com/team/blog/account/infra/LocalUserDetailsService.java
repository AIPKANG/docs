package com.team.blog.account.infra;

import com.team.blog.account.application.EmailRules;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.shared.security.MemberPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폼 로그인 사용자 조회: 정규화 이메일로 LOCAL 로그인 수단을 찾아 {@link MemberPrincipal}(이름 = memberId)을 만든다.
 * 없는 이메일은 {@code DaoAuthenticationProvider}가 더미 해시와 비교해 응답 시간 차이를 줄인다(SC-006).
 */
@Component
public class LocalUserDetailsService implements UserDetailsService {

    private final AuthIdentityRepository authIdentityRepository;
    private final MemberRepository memberRepository;

    public LocalUserDetailsService(AuthIdentityRepository authIdentityRepository, MemberRepository memberRepository) {
        this.authIdentityRepository = authIdentityRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AuthIdentity identity = authIdentityRepository.findLocalByEmail(EmailRules.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        Member member = memberRepository.findById(identity.getMemberId())
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        return MemberPrincipal.withPassword(member.getId(), member.getRole().name(), identity.getPasswordHash());
    }
}
