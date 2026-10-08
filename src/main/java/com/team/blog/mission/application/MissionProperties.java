package com.team.blog.mission.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 같은 주제로 쓰기(034). 기간은 1~30일, 한 사람이 하루에 열 수 있는 미션 수. */
@ConfigurationProperties("blog.missions")
public record MissionProperties(@DefaultValue("true") boolean enabled, @DefaultValue("30") int maxDays,
                                @DefaultValue("3") int perDay) {
}
