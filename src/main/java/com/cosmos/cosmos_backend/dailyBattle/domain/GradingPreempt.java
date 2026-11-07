package com.cosmos.cosmos_backend.dailyBattle.domain;

import java.util.UUID;

public record GradingPreempt(
        Long participationId,
        UUID leaseToken
) {
}
