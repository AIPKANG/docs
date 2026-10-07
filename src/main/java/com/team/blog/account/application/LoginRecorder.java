package com.team.blog.account.application;

import com.team.blog.account.infra.AuthIdentityRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 성공 시 {@code auth_identity.last_login_at} 기록(FR-029). */
@Service
public class LoginRecorder {

    private final AuthIdentityRepository authIdentityRepository;
    private final Clock clock;

    public LoginRecorder(AuthIdentityRepository authIdentityRepository, Clock clock) {
        this.authIdentityRepository = authIdentityRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordLogin(long memberId) {
        authIdentityRepository.findByMemberId(memberId).ifPresent(identity -> identity.recordLogin(clock.instant()));
    }
}
