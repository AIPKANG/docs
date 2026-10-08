package com.team.blog.shared.event;

/** 탈퇴 유예 중 복구됨(023 FR-022). 커밋 후 복구 완료 메일. */
public record MemberRestored(long memberId, String email) implements DomainEvent {
}
