package com.cosmos.cosmos_backend.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // 카페인 캐시 매니저
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {

        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 캐시 지표용
        manager.setCacheNames(List.of("dailyProblems"));

        // 최대 2개 항목 저장, 만료 4시간
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .recordStats());
        return manager;
    }

    // 배틀 제출 API 전용 로컬 캐시
    @Bean
    public CacheManager battleSubmissionCacheManager() {

        CaffeineCacheManager battleSubmissionInfoCacheManager = new CaffeineCacheManager("battleSubmissionInfo");

        battleSubmissionInfoCacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
        );

        return battleSubmissionInfoCacheManager;
    }

}
