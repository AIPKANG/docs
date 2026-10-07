package com.team.blog.account.application;

import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.MemberRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보를 따로 조인하지 않는 기능(알림 등)용 표시 조회. 회원 여러 명을 {@code IN} 한 번으로 가져온다(N+1 금지).
 * 탈퇴·익명 처리 회원도 포함한다(표시만 "탈퇴한 사용자").
 */
@Service
public class MemberSummaryQuery {

    private final MemberRepository memberRepository;

    public MemberSummaryQuery(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, AuthorDisplay> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, AuthorDisplay> result = new HashMap<>();
        for (Member member : memberRepository.findAllById(Set.copyOf(ids))) {
            result.put(member.getId(), AuthorDisplay.of(member.getHandle(), member.getNickname(), member.getWithdrawnAt(),
                    member.getProfileImageUrl()));
        }
        return result;
    }
}
