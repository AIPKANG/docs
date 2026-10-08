package com.team.blog.discovery.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 조회수(016, 31 §2-2·§3). 중복 기준·봇 목록·보관 기간은 설정값(헌법 II).
 *
 * @param notice 글 상세 안내 문구(설정을 바꾸면 함께 바꾼다)
 */
@ConfigurationProperties("blog.view")
public record ViewProperties(@DefaultValue("24h") Duration dedupeWindow,
                             @DefaultValue("1") int maxPerWindow,
                             @DefaultValue("같은 사람은 하루에 한 번만 세요") String notice,
                             @DefaultValue({"bot", "crawler", "spider", "preview", "facebookexternalhit", "Slackbot",
                                     "kakaotalk-scrap", "Discordbot", "HeadlessChrome"}) List<String> botUserAgents,
                             @DefaultValue("60") int perMinute,
                             @DefaultValue("true") boolean flushEnabled,
                             @DefaultValue("1m") Duration flushInterval,
                             @DefaultValue("90d") Duration dailyRetention,
                             @DefaultValue("0 0 5 * * *") String retentionCron) {
}
