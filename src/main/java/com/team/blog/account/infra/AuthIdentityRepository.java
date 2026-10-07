package com.team.blog.account.infra;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 로그인 수단 저장소. account 모듈 안에서만 쓴다(헌법 I). */
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    /** {@code uq_auth_identity (provider, provider_user_id)}. */
    Optional<AuthIdentity> findByProviderAndProviderUserId(Provider provider, String providerUserId);

    /** 이메일 가입 계정(정규화 이메일 = provider_user_id). */
    default Optional<AuthIdentity> findLocalByEmail(String normalizedEmail) {
        return findByProviderAndProviderUserId(Provider.LOCAL, normalizedEmail);
    }

    /**
     * 같은 이메일의 다른 수단 계정(FR-033 안내·재설정 메일의 소셜 안내용). 인증된 이메일만, 탈퇴(유예·익명 처리) 회원 제외.
     * {@code ix_auth_identity_email} 사용.
     */
    @Query("select a from AuthIdentity a, Member m where m.id = a.memberId and a.email = :email"
            + " and a.provider <> :provider and a.emailVerifiedAt is not null"
            + " and m.status <> com.team.blog.account.domain.MemberStatus.WITHDRAWN")
    List<AuthIdentity> findByEmailAndProviderNot(@Param("email") String normalizedEmail,
                                                 @Param("provider") Provider provider);

    Optional<AuthIdentity> findByMemberId(long memberId);
}
