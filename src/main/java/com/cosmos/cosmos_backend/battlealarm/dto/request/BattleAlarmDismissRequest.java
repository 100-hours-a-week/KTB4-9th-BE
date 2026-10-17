package com.cosmos.cosmos_backend.battlealarm.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** 배틀 알림 닫기 요청. dismissed는 true만 허용. */
public record BattleAlarmDismissRequest(
        @NotNull(message = "dismissed_is_required")
        @AssertTrue(message = "invalid_dismissed")
        Boolean dismissed
) {
}
