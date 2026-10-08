package com.team.blog.account.application;

import com.team.blog.shared.event.MemberRestored;
import com.team.blog.shared.event.MemberWithdrawalRequested;
import com.team.blog.shared.web.KoreanDateFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴 신청 → 모든 기기 세션 끊기·접수 메일, 복구 → 완료 메일(023 FR-011·FR-016·FR-022). 실패해도 신청·복구는 그대로. */
@Component
public class WithdrawalListeners {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalListeners.class);

    private final SessionRevoker sessionRevoker;
    private final MailSender mailSender;

    public WithdrawalListeners(SessionRevoker sessionRevoker, MailSender mailSender) {
        this.sessionRevoker = sessionRevoker;
        this.mailSender = mailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MemberWithdrawalRequested e) {
        try {
            sessionRevoker.revokeAll(e.memberId());
        } catch (RuntimeException ex) {
            log.warn("탈퇴 회원 세션 삭제 실패: memberId={} cause={}", e.memberId(), ex.getClass().getSimpleName());
        }
        if (e.email() == null) {
            return;
        }
        try {
            mailSender.send(e.email(), "[블로그] 회원 탈퇴 신청을 받았어요", "mail/withdraw-requested",
                    Map.of("deadline", KoreanDateFormatter.dateTime(e.restoreDeadline())));
        } catch (RuntimeException ex) {
            log.warn("탈퇴 접수 메일 실패: memberId={} cause={}", e.memberId(), ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MemberRestored e) {
        if (e.email() == null) {
            return;
        }
        try {
            mailSender.send(e.email(), "[블로그] 계정이 복구됐어요", "mail/restored", Map.of());
        } catch (RuntimeException ex) {
            log.warn("복구 완료 메일 실패: memberId={} cause={}", e.memberId(), ex.getClass().getSimpleName());
        }
    }
}
