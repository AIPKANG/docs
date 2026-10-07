package com.team.blog.account.application;

import com.team.blog.account.infra.MemberRepository;
import com.team.blog.media.application.ImageReferenceLookup;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** media 정리 작업에 "회원 프로필이 참조 중인 사진"을 알려 준다(SPI 구현, 헌법 I). {@code IN} 한 번. */
@Component
public class ProfileImageReferences implements ImageReferenceLookup {

    private final MemberRepository memberRepository;

    public ProfileImageReferences(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> referencedIds(Collection<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(memberRepository.findProfileImageIdsIn(imageIds));
    }
}
