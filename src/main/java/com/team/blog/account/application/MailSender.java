package com.team.blog.account.application;

import java.util.Map;

/**
 * 메일 발송 포트(research R-13). 개발·테스트는 Mailpit, 운영은 Gmail SMTP — 어댑터 설정만 다르다.
 * 구현은 실패해도 예외를 밖으로 던지지 않고 마스킹한 로그만 남긴다(헌법 V: 부가 처리 실패가 결과를 바꾸지 않음).
 * 트랜잭션 안에서 부르지 않는다 — 커밋 후 리스너에서만 부른다.
 */
public interface MailSender {

    /**
     * @param to        수신 주소
     * @param subject   제목
     * @param template  Thymeleaf 메일 템플릿 이름(예: {@code mail/verify})
     * @param variables 템플릿 변수(링크 등). 로그에 남기지 않는다
     */
    void send(String to, String subject, String template, Map<String, Object> variables);
}
