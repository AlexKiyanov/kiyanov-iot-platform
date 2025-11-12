package com.github.alexkiyanov.iotplatform.dcs.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    @Value("${app.cache.deviceInfoTtl:1440}")
    private int deviceInfoTtlMinutes;

    @Bean
    public Cache<String, Boolean> deviceInfoCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(deviceInfoTtlMinutes))
                .build();
    }
}
