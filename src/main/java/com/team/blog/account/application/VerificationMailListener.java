package com.team.blog.account.application;

import com.team.blog.shared.event.VerificationMailRequested;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 인증 메일 발송(커밋 후 — 헌법 V). 발송 실패는 가입·재발송 결과를 바꾸지 않는다(재발송으로 회복).
 * 트랜잭션 밖에서 발행된 이벤트도 처리한다({@code fallbackExecution}).
 */
@Component
public class VerificationMailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailListener.class);

    private final MailSender mailSender;
    private final AuthProperties properties;

    public VerificationMailListener(MailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(VerificationMailRequested event) {
        try {
            String link = properties.mail().linkBaseUrl() + "/auth/verify?token=" + event.rawToken();
            mailSender.send(event.email(), "[블로그] 이메일 인증을 완료해 주세요", "mail/verify",
                    Map.of("link", link, "hours", properties.verifyTokenTtl().toHours()));
        } catch (RuntimeException e) {
            log.warn("인증 메일 처리 실패: memberId={} cause={}", event.memberId(), e.getClass().getSimpleName());
        }
    }

}
