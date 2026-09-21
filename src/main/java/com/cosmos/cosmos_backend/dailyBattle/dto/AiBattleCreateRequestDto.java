package com.cosmos.cosmos_backend.dailyBattle.dto;

import com.cosmos.cosmos_backend.common.Category;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiBattleCreateRequestDto(
        Category category,
        String problemTitle,
        String problemDescription,

        @JsonProperty("testCases")
        List<AiBattleCaseCreateRequestDto> cases
) {
}
