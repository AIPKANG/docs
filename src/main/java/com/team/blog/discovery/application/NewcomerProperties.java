package com.team.blog.discovery.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 홈 "새로 온 작가"(036, 강성찬 개인 확장): 첫 공개 글이 이 기간 안이면 새 작가, 최대 몇 명. */
@ConfigurationProperties("blog.home.newcomers")
public record NewcomerProperties(@DefaultValue("true") boolean enabled, @DefaultValue("14d") Duration window,
                                 @DefaultValue("6") int limit) {
}
