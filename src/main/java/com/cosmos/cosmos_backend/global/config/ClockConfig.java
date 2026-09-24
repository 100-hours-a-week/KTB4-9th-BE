package com.cosmos.cosmos_backend.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 날짜 계산의 기준 시계 (KST). 테스트에서는 고정 시계로 바꿔 끼운다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
