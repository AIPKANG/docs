package com.team.blog.account.application;

import com.team.blog.account.domain.BioRules;
import com.team.blog.account.domain.BioViolation;
import com.team.blog.shared.text.BannedWordFilter;
import org.springframework.stereotype.Service;

/** 소개 검사 한곳(11 §3, FR-009~FR-011). 금칙어는 닉네임과 같은 필터(예약어 검사 없음). */
@Service
public class BioPolicy {

    /** @param normalized 저장할 값(빈 값이면 NULL로 저장) @param violation 첫 위반, 없으면 null */
    public record BioCheckResult(String normalized, BioViolation violation) {

        public boolean valid() {
            return violation == null;
        }
    }

    private final BannedWordFilter bannedWordFilter;
    private final ProfileProperties.Bio limits;

    public BioPolicy(BannedWordFilter bannedWordFilter, ProfileProperties properties) {
        this.bannedWordFilter = bannedWordFilter;
        this.limits = properties.bio();
    }

    public BioCheckResult check(String raw) {
        String normalized = BioRules.normalize(raw);
        BioViolation violation = BioRules.firstViolation(normalized, limits.maxLength(), limits.maxLines(), bannedWordFilter)
                .orElse(null);
        return new BioCheckResult(normalized, violation);
    }
}
