package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record BattleDropOutResponseDto(

        @JsonProperty("battle_id")
        @Schema(
                description = "중도 포기한 배틀의 ID",
                example = "1"
        )
        Long battleId,

        @JsonProperty("participation_id")
        @Schema(
                description = "중도 포기한 배틀 참여 기록의 ID",
                example = "1"
        )
        Long participationId,

        @JsonProperty("participation_status")
        @Schema(
                description = "배틀 참여 상태",
                example = "DROPPED_OUT"
        )
        ParticipationStatus participationStatus
) {
}
