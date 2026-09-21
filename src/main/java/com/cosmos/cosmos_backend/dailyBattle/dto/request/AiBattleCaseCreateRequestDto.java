package com.cosmos.cosmos_backend.dailyBattle.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AiBattleCaseCreateRequestDto(

        @NotBlank
        String input,

        @NotBlank
        String output
) {
}
