package com.github.alexkiyanov.iotplatform.ecs.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Boolean> deviceIdCache(@Value("${spring.app.cache.deviceIdTtl}") long ttlMinutes) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(1_000_000).build();
    }
}
