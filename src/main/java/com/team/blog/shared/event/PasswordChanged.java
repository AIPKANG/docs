package com.team.blog.shared.event;

/**
 * 비밀번호 변경 완료(003 FR-027). 커밋 후 다른 기기 세션 삭제와 알림 메일을 처리한다.
 *
 * @param keepSessionId 남길 세션(지금 기기). 표현 계층이 이후 ID를 새로 발급한다
 */
public record PasswordChanged(long memberId, String email, String keepSessionId) implements DomainEvent {

    @Override
    public String toString() {
        return "PasswordChanged[memberId=" + memberId + ", email=" + DomainEvent.maskEmail(email) + "]";
    }
}
