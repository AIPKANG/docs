package com.team.blog.discovery.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 트렌딩(019, 32 §2~§4). 가중치·기간·개수·주기는 설정(헌법 II). */
@ConfigurationProperties("blog.trending")
public record TrendingProperties(@DefaultValue("7d") Duration window,
                                 @DefaultValue("3") double likeWeight,
                                 @DefaultValue("2") double commenterWeight,
                                 @DefaultValue("0.1") double viewWeight,
                                 @DefaultValue("2") double hourOffset,
                                 @DefaultValue("1.5") double gravity,
                                 @DefaultValue("3") int perAuthor,
                                 @DefaultValue("100") int top,
                                 @DefaultValue("9") int pageSize,
                                 @DefaultValue("30m") Duration snapshotTtl,
                                 @DefaultValue("true") boolean refreshEnabled,
                                 @DefaultValue("10m") Duration refreshInterval) {
}
