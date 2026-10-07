package com.team.blog.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업 사용(003 사진 정리 작업). 작업마다 자기 설정값으로 켜고 끈다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
