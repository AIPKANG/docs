package com.team.blog.shared.event;

/** 인증 메일 발송 요청(가입·재발송 커밋 후 처리). 원문 토큰은 메일 본문에만 쓰고 로그에 남기지 않는다(FR-014). */
public record VerificationMailRequested(long memberId, String email, String rawToken) implements DomainEvent {

    @Override
    public String toString() {
        return "VerificationMailRequested[memberId=" + memberId + ", email=" + DomainEvent.maskEmail(email)
                + ", rawToken=" + DomainEvent.mask(rawToken) + "]";
    }
}
