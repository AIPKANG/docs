package com.team.blog.account.application;

import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.NicknameRules;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.NicknameChangeTooSoonException;
import com.team.blog.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 변경(research R-12). 003 {@code ProfileService}가 자기 트랜잭션 안에서 호출한다.
 *
 * <p>{@code memberId}는 호출자가 {@code CurrentUser}에서 꺼낸 값만 넘긴다 — 요청 본문의 회원 식별자를 받는 연산은 없다(헌법 III).
 * 호출 전 권한 검사는 {@code AccountGuard.requireLoggedIn}.
 */
@Service
public class NicknameChangeService {

    private final MemberRepository memberRepository;
    private final NicknamePolicy nicknamePolicy;
    private final Clock clock;
    private final Duration cooldown;

    public NicknameChangeService(MemberRepository memberRepository, NicknamePolicy nicknamePolicy, Clock clock,
                                 AccountIdentityProperties properties) {
        this.memberRepository = memberRepository;
        this.nicknamePolicy = nicknamePolicy;
        this.clock = clock;
        this.cooldown = properties.nickname().changeCooldown();
    }

    /**
     * 행 잠금 → 같은 값이면 {@code Unchanged} → 30일 검사 → {@link NicknamePolicy#validate}(본인 제외) → 저장.
     * 대소문자만 바꿔도 변경으로 친다. 예외가 나면 호출자 트랜잭션 전체가 롤백된다({@code REQUIRED}).
     * DB {@code uq_member_nickname} 경합은 {@code saveAndFlush}로 이 메서드 안에서 드러나며, 호출자가 트랜잭션 밖에서
     * {@link MemberUniqueViolationTranslator#translate(org.springframework.dao.DataIntegrityViolationException)}로 바꾼다.
     *
     * @throws NicknameChangeTooSoonException 제한 기간 안
     * @throws com.team.blog.shared.error.NicknameViolationException 규칙 위반
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public NicknameChangeResult change(long memberId, String raw) {
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(NotFoundException::new);
        String normalized = NicknameRules.normalize(raw);
        if (normalized.equals(member.getNickname())) {
            return NicknameChangeResult.UNCHANGED;
        }
        Instant now = clock.instant();
        Optional<Instant> next = nextAllowedAt(member, now);
        if (next.isPresent()) {
            throw new NicknameChangeTooSoonException(next.get());
        }
        Nickname nickname = nicknamePolicy.validate(raw, memberId);
        member.changeNickname(nickname, now);
        memberRepository.saveAndFlush(member);
        return new NicknameChangeResult.Changed(now);
    }

    /** 제한 중이면 다음 변경 가능 시각, 지금 바꿀 수 있으면 empty. */
    @Transactional(readOnly = true)
    public Optional<Instant> nextAllowedAt(long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(NotFoundException::new);
        return nextAllowedAt(member, clock.instant());
    }

    private Optional<Instant> nextAllowedAt(Member member, Instant now) {
        Instant changedAt = member.getNicknameChangedAt();
        if (changedAt == null) {
            return Optional.empty();
        }
        Instant next = changedAt.plus(cooldown);
        return next.isAfter(now) ? Optional.of(next) : Optional.empty();
    }
}
