package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAgreementRepository extends JpaRepository<MemberAgreement, MemberAgreement.Id> {

    List<MemberAgreement> findByIdMemberId(long memberId);
}
