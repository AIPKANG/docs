package com.team.blog.account.infra;

import com.team.blog.account.application.AuthProperties;
import com.team.blog.account.application.MailSender;
import com.team.blog.shared.event.DomainEvent;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * {@link JavaMailSender} + Thymeleaf 메일 템플릿. From은 {@code blog.auth.mail.from}으로 고정(운영은 Gmail 계정 주소).
 * 실패 시 예외를 삼키고, 수신 주소를 가린 로그만 남긴다. 토큰·링크는 로그에 남기지 않는다(FR-014).
 */
@Component
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final ITemplateEngine templateEngine;
    private final AuthProperties properties;

    public SmtpMailSender(JavaMailSender javaMailSender, ITemplateEngine templateEngine, AuthProperties properties) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String template, Map<String, Object> variables) {
        try {
            String html = templateEngine.process(template, new Context(Locale.KOREAN, variables));
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(toPlainText(html), html);
            javaMailSender.send(message);
            log.info("메일 발송: template={} to={}", template, DomainEvent.maskEmail(to));
        } catch (Exception e) {
            // 예외 메시지에 수신 주소가 들어갈 수 있어 종류만 남긴다
            log.warn("메일 발송 실패: template={} to={} cause={}", template, DomainEvent.maskEmail(to),
                    e.getClass().getSimpleName());
        }
    }

    static String toPlainText(String html) {
        String text = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'");
        return text.replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").strip();
    }
}
