package com.cosmos.cosmos_backend.global.config;

import java.time.LocalTime;

/**
 * 하루 배틀에서 사용하는 시각을 한 곳에서 관리한다.
 * 세 시각은 같은 날짜 안에서 시작 < 제출 마감 < 결과 공개 순서여야 한다.
 */
public record BattleSchedule(
        LocalTime openTime,
        LocalTime submissionDeadline,
        LocalTime resultTime
) {

    public BattleSchedule {
        if (openTime == null || submissionDeadline == null || resultTime == null) {
            throw new IllegalArgumentException("battle schedule times must not be null");
        }
        if (!openTime.isBefore(submissionDeadline)
                || !submissionDeadline.isBefore(resultTime)) {
            throw new IllegalArgumentException(
                    "battle schedule must satisfy open-time < submission-deadline < result-time"
            );
        }
    }

    public String openCron() {
        return dailyCron(openTime);
    }

    public String submissionDeadlineCron() {
        return dailyCron(submissionDeadline);
    }

    public String resultCron() {
        return dailyCron(resultTime);
    }

    private String dailyCron(LocalTime time) {
        return "%d %d %d * * *".formatted(
                time.getSecond(),
                time.getMinute(),
                time.getHour()
        );
    }
}
