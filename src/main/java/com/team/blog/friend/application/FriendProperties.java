package com.team.blog.friend.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 친구(025). 한 회원이 하루에 보낼 수 있는 친구 요청 수(요청은 상대에게 알림이 가므로 남발을 막는다). */
@ConfigurationProperties("blog.friend")
public record FriendProperties(@DefaultValue("50") int dailyRequestLimit) {
}
