package com.team.blog.account.application;

import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.MemberSuspension;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.MemberSuspensionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시점 계정 상태 판정(FR-030, research R-9) — 폼·소셜 공용. 비밀번호(또는 소셜 인증)가 맞은 뒤에만 부른다.
 * <ul>
 *   <li>정지 판정은 {@code member_suspension}: {@code lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now)}</li>
 *   <li>기간이 지난 정지는 이 쓰기 트랜잭션에서 자동 해제({@code lifted_at = now}, {@code lifted_by = NULL}),
 *       {@code status='SUSPENDED'}였으면 {@code ACTIVE}로 되돌린다</li>
 *   <li>탈퇴 유예({@code status='WITHDRAWN' AND deleted_at IS NULL}) → 복구 전용 세션</li>
 * </ul>
 */
@Service
public class AccountStatusChecker {

    private final MemberRepository memberRepository;
    private final MemberSuspensionRepository suspensionRepository;
    private final Clock clock;

    public AccountStatusChecker(MemberRepository memberRepository, MemberSuspensionRepository suspensionRepository,
                                Clock clock) {
        this.memberRepository = memberRepository;
        this.suspensionRepository = suspensionRepository;
        this.clock = clock;
    }

    @Transactional
    public LoginStatus checkOnLogin(long memberId) {
        Instant now = clock.instant();
        Optional<MemberSuspension> current = suspensionRepository.findCurrent(memberId, now);
        if (current.isPresent()) {
            return new LoginStatus.Suspended(current.get().getEndsAt(), current.get().getReason());
        }
        List<MemberSuspension> expired = suspensionRepository.findExpiredNotLifted(memberId, now);
        Optional<Member> member = memberRepository.findById(memberId);
        if (!expired.isEmpty()) {
            expired.forEach(s -> s.liftAutomatically(now));
            member.ifPresent(m -> m.returnToActiveAfterSuspension(now));
        }
        if (member.map(Member::isWithdrawalPending).orElse(false)) {
            return new LoginStatus.WithdrawnPending();
        }
        return new LoginStatus.Active();
    }
}
