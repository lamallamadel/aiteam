package com.atlasia.ai.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("analytics-runs-summary"),
                new ConcurrentMapCache("analytics-agents-performance"),
                new ConcurrentMapCache("analytics-personas-findings"),
                new ConcurrentMapCache("analytics-fix-loops")
        ));
        return cacheManager;
    }
}
