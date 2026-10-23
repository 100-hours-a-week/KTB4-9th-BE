package com.cosmos.cosmos_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public BattleSchedule battleSchedule(
            @Value("${battle.schedule.open-time:12:50:00}") LocalTime openTime,
            @Value("${battle.schedule.submission-deadline:12:59:59}") LocalTime submissionDeadline,
            @Value("${battle.schedule.result-time:13:00:00}") LocalTime resultTime
    ) {
        return new BattleSchedule(openTime, submissionDeadline, resultTime);
    }
}
