package com.team.blog.shared.error;

import com.team.blog.account.domain.NicknameViolation;

/**
 * 닉네임 규칙 위반 → 400 {@code {code}}, 경합에서 진 쪽({@code concurrent})은 409.
 * 금칙어 위반이어도 걸린 단어나 입력값을 담지 않는다(SC-006).
 */
public class NicknameViolationException extends RuntimeException {

    private final NicknameViolation code;
    private final boolean concurrent;

    public NicknameViolationException(NicknameViolation code) {
        this(code, false);
    }

    public NicknameViolationException(NicknameViolation code, boolean concurrent) {
        super(code.name());
        this.code = code;
        this.concurrent = concurrent;
    }

    public NicknameViolation getCode() {
        return code;
    }

    /** DB {@code uq_member_nickname} 경합에서 진 쪽이면 참("방금 다른 분이 이 닉네임을 사용했어요"). */
    public boolean isConcurrent() {
        return concurrent;
    }
}
