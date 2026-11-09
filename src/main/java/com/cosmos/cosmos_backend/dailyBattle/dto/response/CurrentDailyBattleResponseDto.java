package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record CurrentDailyBattleResponseDto(
        @JsonProperty("server_time")
        LocalDateTime serverTime,

        @JsonProperty("current_battle_id")
        Long currentBattleId,

        @JsonProperty("latest_result_battle_id")
        Long latestResultBattleId

) {}
