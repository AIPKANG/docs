package com.team.blog.account.application;

import com.team.blog.account.domain.HandleRules;
import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.MemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /@{handle}} 주인 찾기(research R-15). 형식에 맞지 않는 값은 조회 없이 empty, 탈퇴(유예·익명 처리)는 empty.
 * empty면 호출자가 {@code NotFoundException}(404 공통 화면)을 던진다 — 없음과 탈퇴를 구분하지 않는다(헌법 III).
 */
@Service
public class BlogOwnerResolver {

    private final MemberRepository memberRepository;

    public BlogOwnerResolver(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BlogOwner> resolve(String handle) {
        if (!HandleRules.isValidFormat(handle)) {
            return Optional.empty();
        }
        return memberRepository.findActiveByHandle(handle).map(BlogOwnerResolver::toOwner);
    }

    private static BlogOwner toOwner(Member member) {
        return new BlogOwner(member.getId(), member.getHandle(), member.getNickname(), member.getBio(),
                member.getProfileImageUrl());
    }
}
