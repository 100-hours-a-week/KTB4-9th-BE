package com.cosmos.cosmos_backend.battlealarm.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 배틀 알림 닫기 응답. */
public record BattleAlarmDismissResponse(
        @JsonProperty("battle_id") Long battleId,
        boolean dismissed
) {
}
