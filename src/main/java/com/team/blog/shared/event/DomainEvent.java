package com.team.blog.shared.event;

/**
 * 도메인 이벤트 표시 인터페이스. 부가 처리(메일·알림 등)는 커밋 후에 구독한다(헌법 V).
 * 토큰 같은 비밀 필드를 가진 이벤트는 {@code toString()}에서 그 값을 가린다(FR-014).
 */
public interface DomainEvent {

    /** 로그용 마스킹: 앞 2글자만 남기고 가린다. */
    static String mask(String secret) {
        if (secret == null) {
            return "null";
        }
        return secret.length() <= 2 ? "***" : secret.substring(0, 2) + "***";
    }

    /** 이메일 마스킹: {@code ab***@domain}. */
    static String maskEmail(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return mask(email);
        }
        return mask(email.substring(0, at)) + email.substring(at);
    }
}
