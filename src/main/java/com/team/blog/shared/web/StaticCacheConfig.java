package com.team.blog.shared.web;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 바뀌지 않는 정적 파일(글꼴, 버전이 주소에 든 webjar)은 브라우저가 오래 보관하게 한다.
 * 보안 기본값(no-store)이면 새로고침마다 글꼴을 다시 받아 잠깐 기본 글꼴이 보인다(디자인 시안 10-08).
 * 응답에 Cache-Control이 이미 있으면 Spring Security는 덮어쓰지 않는다. css·js는 배포 때 바뀌므로 그대로 둔다.
 */
@Configuration
public class StaticCacheConfig implements WebMvcConfigurer {

    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fonts/**").addResourceLocations("classpath:/static/fonts/").setCacheControl(IMMUTABLE);
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/").setCacheControl(IMMUTABLE);
    }
}
