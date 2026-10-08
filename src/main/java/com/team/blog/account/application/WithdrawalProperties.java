package com.team.blog.account.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 회원 탈퇴(023, 13·44). 비밀번호 실패 잠금은 11 §6-2와 같은 값. */
@ConfigurationProperties("blog.withdraw")
public record WithdrawalProperties(@DefaultValue("30d") Duration grace,
                                   @DefaultValue("5") int maxFailures,
                                   @DefaultValue("15m") Duration lockDuration,
                                   @DefaultValue("true") boolean purgeEnabled,
                                   @DefaultValue("0 0 3 * * *") String purgeCron,
                                   @DefaultValue("100") int purgeBatch) {
}
