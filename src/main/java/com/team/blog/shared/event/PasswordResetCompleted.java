package com.team.blog.shared.event;

/** 재설정으로 새 비밀번호 저장(커밋 후 그 회원의 모든 세션 삭제 — FR-019). */
public record PasswordResetCompleted(long memberId) implements DomainEvent {
}
