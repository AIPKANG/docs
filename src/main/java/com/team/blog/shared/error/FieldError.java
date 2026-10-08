package com.team.blog.shared.error;

import java.time.Instant;
import java.util.Objects;

/**
 * 칸별 검사 실패 하나(003 research R-4). 문구는 {@link GlobalExceptionHandler}가 {@code messageKey}로 찾는다.
 * 걸린 금칙어는 담지 않는다. 입력값은 태그처럼 여러 개 중 무엇이 틀렸는지 보여야 할 때만 {@code value}로 돌려준다(013 FR-007).
 *
 * @param field         칸 이름(요청 JSON 키)
 * @param code          오류 코드
 * @param messageKey    문구 키({@code error.X} 또는 {@code password.X})
 * @param nextAllowedAt 닉네임 다음 변경 가능 시각(해당할 때만)
 * @param conflict      충돌 종류(30일 제한·동시 경합)인지 — 모두 충돌이면 409
 * @param value         사용자가 보낸 값(태그 오류만, 없으면 null)
 */
public record FieldError(String field, String code, String messageKey, Instant nextAllowedAt, boolean conflict,
                         String value) {

    public FieldError(String field, String code, String messageKey, Instant nextAllowedAt, boolean conflict) {
        this(field, code, messageKey, nextAllowedAt, conflict, null);
    }

    /** 013: 태그처럼 입력값을 함께 돌려줄 오류. */
    public static FieldError withValue(String field, String code, String value) {
        return new FieldError(field, code, "error." + code, null, false, value);
    }

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
