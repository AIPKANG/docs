package com.team.blog.account.application;

import com.team.blog.account.domain.PasswordPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 정책·인코더 Bean(research R-4): {@code DelegatingPasswordEncoder}의 BCrypt(강도 설정값, {@code {bcrypt}} 접두). */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(AuthProperties properties) {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(properties.password().bcryptStrength());
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
    }

    @Bean
    public PasswordPolicy passwordPolicy(AuthProperties properties, ResourceLoader resourceLoader) throws IOException {
        try (InputStream in = resourceLoader.getResource(properties.password().commonPasswordsLocation()).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new PasswordPolicy(Arrays.asList(text.split("\\R")), properties.password().localPartMinLength());
        }
    }
}
