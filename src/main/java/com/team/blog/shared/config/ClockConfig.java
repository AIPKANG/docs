package com.team.blog.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 규칙(닉네임 30일 제한 등)은 모두 이 {@link Clock}을 주입받아 쓴다. 테스트는 MutableClock으로 대체한다. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
