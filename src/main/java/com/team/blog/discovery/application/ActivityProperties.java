package com.team.blog.discovery.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 잔디·스트릭(031, 강성찬 개인 확장). {@code enabled=false}면 블로그에 보이지 않는다. */
@ConfigurationProperties("blog.activity")
public record ActivityProperties(@DefaultValue("true") boolean enabled, @DefaultValue("Asia/Seoul") String zone) {
}
