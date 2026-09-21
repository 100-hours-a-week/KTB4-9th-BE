package com.cosmos.cosmos_backend.dailyBattle.dto;

public record BattleCaseResponseDto(
        Long battleCaseId,
        String input,
        Integer displayOrder
) {
}
