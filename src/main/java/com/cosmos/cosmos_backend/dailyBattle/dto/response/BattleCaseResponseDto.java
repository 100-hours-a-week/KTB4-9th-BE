package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record BattleCaseResponseDto(
        @Schema(description = "공개 테스트 케이스 입력", example = "1 2")
        String input,
        @Schema(description = "테스트 케이스 표시 순서", example = "1")
        Integer displayOrder
) {
}
