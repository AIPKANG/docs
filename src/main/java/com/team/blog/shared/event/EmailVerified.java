package com.team.blog.shared.event;

/** 이메일 인증 완료(커밋 후). */
public record EmailVerified(long memberId) implements DomainEvent {
}
