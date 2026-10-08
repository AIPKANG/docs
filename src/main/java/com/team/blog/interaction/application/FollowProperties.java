package com.team.blog.interaction.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 팔로우(018). 팔로우만의 제한은 없고, 공통 IP 요청 제한 기준값({@code blog.rate-limit.per-ip-per-minute})을 그대로 쓴다(FR-020).
 */
@ConfigurationProperties("blog.follow")
public record FollowProperties(@DefaultValue("120") int perIpPerMinute, @DefaultValue("20") int pageSize) {
}
