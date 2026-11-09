package com.cosmos.cosmos_backend.auth.dto;

import java.time.LocalDateTime;

public record WithdrawalResponseDto(
        LocalDateTime withdrawalRequestedAt,

        LocalDateTime scheduledDeletionAt
) {
}
