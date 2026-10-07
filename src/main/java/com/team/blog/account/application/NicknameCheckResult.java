package com.team.blog.account.application;

import com.team.blog.account.domain.NicknameViolation;

/**
 * 닉네임 검사 결과(가입·변경·API 공용).
 *
 * @param normalized 정리(trim+NFC)된 값
 * @param violation  첫 위반, 통과면 null
 */
public record NicknameCheckResult(String normalized, NicknameViolation violation) {

    public boolean available() {
        return violation == null;
    }
}
