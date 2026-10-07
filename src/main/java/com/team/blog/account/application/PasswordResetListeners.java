package com.team.blog.account.application;

import com.team.blog.shared.event.PasswordResetCompleted;
import com.team.blog.shared.event.PasswordResetMailRequested;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋 후 처리(헌법 V): 재설정·소셜 안내 메일, 재설정 완료 시 모든 기기 로그아웃. */
@Component
public class PasswordResetListeners {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetListeners.class);

    private final MailSender mailSender;
    private final SessionRevoker sessionRevoker;
    private final AuthProperties properties;

    public PasswordResetListeners(MailSender mailSender, SessionRevoker sessionRevoker, AuthProperties properties) {
        this.mailSender = mailSender;
        this.sessionRevoker = sessionRevoker;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PasswordResetMailRequested event) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("providers", providerNames(event.otherProviders()));
            if (event.kind() == PasswordResetMailRequested.Kind.LOCAL_LINK) {
                variables.put("link", properties.mail().linkBaseUrl() + "/password/reset?token=" + event.rawToken());
                variables.put("minutes", properties.resetTokenTtl().toMinutes());
                mailSender.send(event.email(), "[블로그] 비밀번호 재설정 안내", "mail/reset", variables);
            } else {
                mailSender.send(event.email(), "[블로그] 비밀번호 찾기 안내", "mail/social-only", variables);
            }
        } catch (RuntimeException e) {
            log.warn("재설정 메일 처리 실패: cause={}", e.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PasswordResetCompleted event) {
        sessionRevoker.revokeAll(event.memberId());
    }

    static List<String> providerNames(List<String> providers) {
        return providers.stream().map(p -> switch (p) {
            case "GOOGLE" -> "Google";
            case "GITHUB" -> "GitHub";
            default -> p;
        }).toList();
    }
}
