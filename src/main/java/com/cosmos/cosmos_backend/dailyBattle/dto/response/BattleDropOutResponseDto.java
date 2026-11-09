package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record BattleDropOutResponseDto(

        @JsonProperty("battle_id")
        Long battleId,

        @JsonProperty("participation_id")
        Long participationId,

        @JsonProperty("participation_status")
        ParticipationStatus participationStatus
) {
}
