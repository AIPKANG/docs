package com.team.blog.account.application;

import com.team.blog.shared.event.DomainEvent;
import com.team.blog.shared.event.PasswordChanged;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 비밀번호 변경 커밋 후 처리(헌법 V): 다른 기기 세션 삭제, "비밀번호가 변경됐어요" 알림 메일(FR-027).
 * 둘은 서로의 실패와 무관하게 실행하고, 실패는 마스킹한 로그만 남긴다.
 */
@Component
public class PasswordChangedListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangedListener.class);

    private final SessionRevoker sessionRevoker;
    private final MailSender mailSender;
    private final AuthProperties properties;

    public PasswordChangedListener(SessionRevoker sessionRevoker, MailSender mailSender, AuthProperties properties) {
        this.sessionRevoker = sessionRevoker;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PasswordChanged event) {
        try {
            sessionRevoker.revokeAllExcept(event.memberId(), event.keepSessionId());
        } catch (RuntimeException e) {
            log.warn("다른 기기 세션 삭제 실패: memberId={} cause={}", event.memberId(), e.getClass().getSimpleName());
        }
        if (event.email() == null) {
            return;
        }
        try {
            mailSender.send(event.email(), "[블로그] 비밀번호가 변경됐어요", "mail/password-changed",
                    Map.of("resetLink", properties.mail().linkBaseUrl() + "/password/forgot"));
        } catch (RuntimeException e) {
            log.warn("비밀번호 변경 알림 메일 실패: to={} cause={}", DomainEvent.maskEmail(event.email()),
                    e.getClass().getSimpleName());
        }
    }
}
