package com.cosmos.cosmos_backend.auth.dto;

import java.time.LocalDateTime;

public record CurrentUserResponseDto(
        Long userId,

        String username,

        String profileImageUrl,

        String accountStatus,

        boolean withdrawalPending,

        LocalDateTime withdrawalRequestedAt,

        LocalDateTime scheduledDeletionAt
) {
}