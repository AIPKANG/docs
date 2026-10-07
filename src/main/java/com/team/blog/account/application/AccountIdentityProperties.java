package com.team.blog.account.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 블로그 주소·닉네임 정책 수치·목록(헌법 II: 코드가 아닌 설정값). 기본값은 {@code application.yml}의 {@code blog.account.*}.
 */
@ConfigurationProperties("blog.account")
public record AccountIdentityProperties(
        @DefaultValue HandleSettings handle,
        @DefaultValue NicknameSettings nickname,
        @DefaultValue AvailabilitySettings availability) {

    /**
     * @param reserved               블로그 주소 예약어(본문 정확 일치, FR-004)
     * @param prefillMaxBodyLength   미리 채우기 7단계 자르기 길이(08 §3)
     * @param suggestionBatchSize    대안 번호 후보를 한 번에 조회하는 개수(research R-3)
     */
    public record HandleSettings(
            @DefaultValue List<String> reserved,
            @DefaultValue("30") int prefillMaxBodyLength,
            @DefaultValue("20") int suggestionBatchSize) {
    }

    /**
     * @param reserved        닉네임 예약어(4변형 포함 검사, FR-017)
     * @param changeCooldown  닉네임 변경 후 다시 바꿀 수 없는 기간(FR-023)
     */
    public record NicknameSettings(
            @DefaultValue List<String> reserved,
            @DefaultValue("30d") Duration changeCooldown) {
    }

    /** @param perIpPerMinute 사용 가능 여부 API의 IP당 1분 한도(FR-009, FR-021) */
    public record AvailabilitySettings(@DefaultValue("30") int perIpPerMinute) {
    }
}
