package com.team.blog.shared.event;

import java.time.Instant;

/** 회원 탈퇴 신청이 저장됨(023 FR-016). 커밋 후 세션을 끊고 접수 메일을 보낸다. */
public record MemberWithdrawalRequested(long memberId, String email, Instant restoreDeadline) implements DomainEvent {
}
