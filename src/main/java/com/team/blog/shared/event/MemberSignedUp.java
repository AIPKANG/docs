package com.team.blog.shared.event;

/** 가입 커밋 후(이메일·소셜 공통). provider는 {@code LOCAL}/{@code GOOGLE}/{@code GITHUB}. */
public record MemberSignedUp(long memberId, String provider) implements DomainEvent {
}
