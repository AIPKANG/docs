package com.team.blog.account.application;

import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.NicknameRules;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.text.BannedWordFilter;
import com.team.blog.shared.text.WordListLoader;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 검사 한곳(09 §3, FR-020). 이메일 가입·소셜 가입(001)과 닉네임 변경(US3)이 같은 정책을 쓴다.
 * ① trim+NFC ② 형식 ③ 글자 포함 ④ 예약어 ⑤ 금칙어 ⑥ 대소문자 무시 중복 — 첫 실패에서 멈춘다.
 */
@Service
public class NicknamePolicy {

    private final MemberRepository memberRepository;
    private final BannedWordFilter bannedWordFilter;
    private final List<String> reserved;

    public NicknamePolicy(MemberRepository memberRepository, BannedWordFilter bannedWordFilter,
                          AccountIdentityProperties properties) {
        this.memberRepository = memberRepository;
        this.bannedWordFilter = bannedWordFilter;
        this.reserved = properties.nickname().reserved().stream()
                .map(String::strip)
                .filter(word -> !word.isEmpty())
                .map(WordListLoader::normalize)
                .distinct()
                .toList();
    }

    /**
     * @param excludeMemberId 중복 검사에서 뺄 회원(닉네임 변경 시 본인), 가입이면 null
     * @throws NicknameViolationException 첫 위반
     */
    @Transactional(readOnly = true)
    public Nickname validate(String raw, Long excludeMemberId) {
        NicknameCheckResult result = check(raw, excludeMemberId);
        if (result.violation() != null) {
            throw new NicknameViolationException(result.violation());
        }
        return Nickname.of(result.normalized());
    }

    @Transactional(readOnly = true)
    public NicknameCheckResult check(String raw, Long excludeMemberId) {
        String normalized = NicknameRules.normalize(raw);
        Optional<NicknameViolation> violation = NicknameRules.firstViolation(normalized, reserved, bannedWordFilter);
        if (violation.isPresent()) {
            return new NicknameCheckResult(normalized, violation.get());
        }
        if (memberRepository.existsByNicknameIgnoreCaseExcluding(normalized, excludeMemberId)) {
            return new NicknameCheckResult(normalized, NicknameViolation.NICKNAME_DUPLICATE);
        }
        return new NicknameCheckResult(normalized, null);
    }

    /** 소셜 이름 미리 채우기(research R-13): 정리 결과가 모든 검사(중복 포함)를 통과할 때만 값, 아니면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<String> suggestFromSocialName(String displayName) {
        return NicknameRules.socialNameCandidate(displayName, reserved, bannedWordFilter)
                .filter(candidate -> check(candidate, null).available());
    }
}
