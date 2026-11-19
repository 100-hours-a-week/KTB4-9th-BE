package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record CurrentDailyBattleResponseDto(
        @JsonProperty("server_time")
        @Schema(description = "서버 시각")
        LocalDateTime serverTime,

        @JsonProperty("current_battle_id")
        @Schema(description = "오늘 배틀 ID. 배틀이 없으면 null", example = "1")
        Long currentBattleId,

        @JsonProperty("latest_result_battle_id")
        @Schema(description = "전날 배틀 ID. 배틀이 없으면 null", example = "2")
        Long latestResultBattleId

) {}
