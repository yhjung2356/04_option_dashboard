package com.trading.dashboard.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정
 * - 종목코드 생성 결과를 캐싱하여 반복 계산 방지
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 캐시 매니저 설정
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(java.util.List.of(
                new ConcurrentMapCache("futuresCodes"),
                new ConcurrentMapCache("optionsCodes"),
                new ConcurrentMapCache("kospi200Index")));
        return cacheManager;
    }
}
