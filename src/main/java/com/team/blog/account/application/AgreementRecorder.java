package com.team.blog.account.application;

import com.team.blog.account.domain.AgreementType;
import com.team.blog.account.domain.MemberAgreement;
import com.team.blog.account.infra.MemberAgreementRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 약관 동의 기록(FR-006). DB에는 "필수 동의" 제약이 없으므로(51 §4) 이 클래스가 보장한다.
 * 동의 분리 결정이 바뀌면 이 한 곳만 바뀐다(research R-14).
 */
@Component
public class AgreementRecorder {

    private final MemberAgreementRepository repository;

    public AgreementRecorder(MemberAgreementRepository repository) {
        this.repository = repository;
    }

    /** 계정을 만들기 전에 부른다. */
    public void requireBoth(boolean agreeTerms, boolean agreePrivacy) {
        if (!agreeTerms || !agreePrivacy) {
            throw new AgreementRequiredException();
        }
    }

    /** 가입 트랜잭션 안에서 {@code TERMS}·{@code PRIVACY} 두 행을 만든다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSignupAgreements(long memberId, Instant now) {
        repository.saveAll(List.of(
                new MemberAgreement(memberId, AgreementType.TERMS, now),
                new MemberAgreement(memberId, AgreementType.PRIVACY, now)));
    }
}
