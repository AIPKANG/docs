package com.team.blog.moderation.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 신고·운영(022, 43). */
@ConfigurationProperties("blog.moderation")
public record ModerationProperties(@DefaultValue("5") int reportsPerMinute,
                                   @DefaultValue("50") int reportsPerDay,
                                   @DefaultValue("2000") int snapshotChars,
                                   @DefaultValue("30d") Duration snapshotRetention,
                                   @DefaultValue("true") boolean cleanupEnabled,
                                   @DefaultValue("0 40 4 * * *") String cleanupCron) {
}
