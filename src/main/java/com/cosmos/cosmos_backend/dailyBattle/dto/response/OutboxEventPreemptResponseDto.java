package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.OutboxEvent;

import java.util.List;
import java.util.UUID;

public record OutboxEventPreemptResponseDto(
        List<OutboxEvent> outboxEventList,

        UUID leaseToken
) {
}
