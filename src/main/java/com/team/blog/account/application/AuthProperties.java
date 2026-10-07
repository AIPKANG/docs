package com.team.blog.account.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 001-auth 정책 수치(헌법 II: 코드가 아닌 설정값). 기본값은 {@code application.yml}의 {@code blog.auth.*}와 같다.
 *
 * @param verifyTokenTtl   인증 링크 유효 시간(FR-008)
 * @param resetTokenTtl    재설정 링크 유효 시간(FR-017)
 * @param verifyResend     인증 메일 재발송 한도(FR-009)
 * @param resetRequest     비밀번호 찾기 요청 한도(FR-018)
 * @param login            로그인 잠금·IP 한도(FR-025)
 * @param sessionTimeout   로그인 세션 최대 비활성 시간(FR-024)
 * @param pendingSocialTtl 소셜 가입 대기 정보 유효 시간(FR-021)
 * @param password         비밀번호 정책 세부값(FR-012, FR-013)
 * @param mail             메일 발신 주소·링크 기준 주소
 */
@ConfigurationProperties("blog.auth")
public record AuthProperties(
        @DefaultValue("24h") Duration verifyTokenTtl,
        @DefaultValue("30m") Duration resetTokenTtl,
        @DefaultValue VerifyResend verifyResend,
        @DefaultValue ResetRequest resetRequest,
        @DefaultValue Login login,
        @DefaultValue("14d") Duration sessionTimeout,
        @DefaultValue("10m") Duration pendingSocialTtl,
        @DefaultValue Password password,
        @DefaultValue Mail mail) {

    /** @param perMinute 1분 한도 @param perDay 하루 한도 */
    public record VerifyResend(@DefaultValue("1") int perMinute, @DefaultValue("10") int perDay) {
    }

    public record ResetRequest(
            @DefaultValue("1") int emailPerMinute,
            @DefaultValue("10") int emailPerDay,
            @DefaultValue("20") int ipPerHour) {
    }

    /**
     * @param maxFailures  연속 실패 몇 번째에 잠그는지
     * @param lockDuration 잠금 시간(실패 카운터 창도 같은 값)
     * @param ipPerMinute  IP당 1분 로그인 시도 한도
     */
    public record Login(
            @DefaultValue("5") int maxFailures,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("20") int ipPerMinute) {
    }

    /**
     * @param localPartMinLength      이메일 앞부분 포함 검사를 적용할 최소 길이(research R-4, U-4)
     * @param bcryptStrength          BCrypt 강도
     * @param commonPasswordsLocation 흔한 비밀번호 목록 위치
     */
    public record Password(
            @DefaultValue("3") int localPartMinLength,
            @DefaultValue("10") int bcryptStrength,
            @DefaultValue("classpath:security/common-passwords.txt") String commonPasswordsLocation) {
    }

    /**
     * @param from        보내는 주소(운영은 Gmail 계정 주소로 고정 — research R-13)
     * @param linkBaseUrl 메일 링크 앞부분(예: {@code https://devlog.com})
     */
    public record Mail(
            @DefaultValue("no-reply@localhost") String from,
            @DefaultValue("http://localhost:8080") String linkBaseUrl) {
    }
}
