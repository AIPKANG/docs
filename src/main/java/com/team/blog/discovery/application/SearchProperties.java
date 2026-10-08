package com.team.blog.discovery.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 검색(020, 33 §4·§5). */
@ConfigurationProperties("blog.search")
public record SearchProperties(@DefaultValue("3000") int recentWindow,
                               @DefaultValue("9") int pageSize,
                               @DefaultValue("20") int peopleLimit,
                               @DefaultValue("30") int perMinute) {
}
