package com.cosmos.cosmos_backend.dailyBattle.domain;

import java.util.UUID;

public record OutboxSentConfirm(
        Long outboxEventId,
        UUID leaseToken
) {
}
