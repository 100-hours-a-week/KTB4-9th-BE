package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;

import java.time.LocalDateTime;

public record BattleSubmissionResponseDto(
        @Schema(description = "답안을 제출한 배틀 ID", example = "1")
        Long battleId,

        @Schema(description = "답안을 제출한 사용자 ID", example = "1")
        Long participantId,

        @Schema(description = "답안 접수 후 참여 상태", example = "SUBMITTED")
        ParticipationStatus participationStatus,

        @Schema(description = "답안 접수 시각")
        LocalDateTime submittedAt
) {
}
