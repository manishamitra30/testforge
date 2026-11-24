package com.testforge.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SUMMARY = "dashboard-summary";
    public static final String TREND = "dashboard-trend";
    public static final String SEVERITY = "dashboard-severity";
    public static final String FLAKY = "dashboard-flaky";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SUMMARY, TREND, SEVERITY, FLAKY);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(3)).maximumSize(500));
        return manager;
    }
}