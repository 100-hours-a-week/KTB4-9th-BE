package com.cosmos.cosmos_backend.dailyBattle.dto.request;

import com.cosmos.cosmos_backend.common.Category;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiBattleCreateRequestDto(

        @NotNull
        Category category,

        @NotBlank
        String problemTitle,

        @NotBlank
        String problemDescription,

        @NotNull
        @JsonProperty("testCases")
        @Size(min = 3, max = 3)
        List<@Valid AiBattleCaseCreateRequestDto> cases
) {
}
