package com.trading.dashboard.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 캐시 설정
 * 시장 데이터의 반복적인 조회를 캐싱하여 성능 개선
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                // 시장 개요 데이터 캐시 (1초 동안 유지)
                new ConcurrentMapCache("marketOverview"),
                // 옵션 체인 데이터 캐시 (1초 동안 유지)
                new ConcurrentMapCache("optionChain"),
                // 시장 상태 캐시 (10초 동안 유지)
                new ConcurrentMapCache("marketStatus")));
        return cacheManager;
    }
}
