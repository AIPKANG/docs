package com.team.blog.friend.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 그룹 공개(030, 강성찬 개인 확장). 한 사람의 그룹 수, 그룹당 인원 상한. {@code enabled=false}면 그룹 공개를 고를 수 없다. */
@ConfigurationProperties("blog.friend.groups")
public record GroupProperties(@DefaultValue("true") boolean enabled, @DefaultValue("20") int maxGroups,
                              @DefaultValue("100") int maxMembers) {
}
