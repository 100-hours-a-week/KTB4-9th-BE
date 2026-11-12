package com.cosmos.cosmos_backend.dailyBattle.consumer.domain;

import java.util.UUID;

public record GradingConsumerPreemptResult(
        Long participationId,

        UUID leaseToken,

        GradingPreemptStatus gradingPreemptStatus

) {
}
