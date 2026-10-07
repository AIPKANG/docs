package com.team.blog.shared.error;

import java.time.Instant;
import java.util.Objects;

/**
 * 칸별 검사 실패 하나(003 research R-4). 문구는 {@link GlobalExceptionHandler}가 {@code messageKey}로 찾는다.
 * 입력값·걸린 금칙어는 담지 않는다.
 *
 * @param field         칸 이름(요청 JSON 키)
 * @param code          오류 코드
 * @param messageKey    문구 키({@code error.X} 또는 {@code password.X})
 * @param nextAllowedAt 닉네임 다음 변경 가능 시각(해당할 때만)
 * @param conflict      충돌 종류(30일 제한·동시 경합)인지 — 모두 충돌이면 409
 */
public record FieldError(String field, String code, String messageKey, Instant nextAllowedAt, boolean conflict) {

    public FieldError {
        Objects.requireNonNull(field);
        Objects.requireNonNull(code);
        Objects.requireNonNull(messageKey);
    }

    public static FieldError of(String field, String code) {
        return new FieldError(field, code, "error." + code, null, false);
    }

    /** 001 비밀번호 정책 위반({@code password.X} 문구). */
    public static FieldError password(String field, String code) {
        return new FieldError(field, code, "password." + code, null, false);
    }

    /** 닉네임 30일 제한(409 종류). */
    public static FieldError nicknameTooSoon(Instant nextAllowedAt) {
        return new FieldError("nickname", NicknameChangeTooSoonException.CODE, "error." + NicknameChangeTooSoonException.CODE,
                nextAllowedAt, true);
    }

    /** 동시 경합에서 진 닉네임(409 종류, "방금 다른 분이 …"). */
    public static FieldError nicknameTakenConcurrently() {
        return new FieldError("nickname", "NICKNAME_DUPLICATE", "error.NICKNAME_DUPLICATE_CONCURRENT", null, true);
    }
}
