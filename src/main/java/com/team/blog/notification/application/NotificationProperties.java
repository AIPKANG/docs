package com.team.blog.notification.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 알림(017, 25 §4~§6). 기준값은 설정(헌법 II).
 *
 * @param async          사건 처리를 요청 스레드 밖(한 줄 대기열)에서 할지. 테스트는 끄고 바로 처리한다
 * @param queueCapacity  대기열 크기. 가득 차면 버리고 경고(유실 허용)
 */
@ConfigurationProperties("blog.notification")
public record NotificationProperties(@DefaultValue("true") boolean async,
                                     @DefaultValue("1000") int queueCapacity,
                                     @DefaultValue("7d") Duration followDedupe,
                                     @DefaultValue("90d") Duration retention,
                                     @DefaultValue("1000") int maxPerMember,
                                     @DefaultValue("1000") int cleanupBatch,
                                     @DefaultValue("true") boolean cleanupEnabled,
                                     @DefaultValue("0 30 4 * * *") String cleanupCron,
                                     @DefaultValue("10") int dropdownSize,
                                     @DefaultValue("20") int pageSize,
                                     @DefaultValue("30") int pollSeconds,
                                     @DefaultValue("50") int previewLength) {
}
