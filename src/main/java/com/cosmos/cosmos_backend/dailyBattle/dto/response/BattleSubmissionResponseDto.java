package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;

import java.time.LocalDateTime;

public record BattleSubmissionResponseDto(
        Long battleId,
        Long participantId,
        ParticipationStatus participationStatus,
        LocalDateTime submittedAt
) {
}
