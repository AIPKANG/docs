package com.team.blog.teaser.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** AI 티저(035). 하루 만들기 횟수, 티저 길이. {@code enabled=false}면 화면이 없다(저장된 티저는 카드에 그대로). */
@ConfigurationProperties("blog.teaser")
public record TeaserProperties(@DefaultValue("true") boolean enabled, @DefaultValue("10") int perDay,
                               @DefaultValue("80") int maxLength) {
}
