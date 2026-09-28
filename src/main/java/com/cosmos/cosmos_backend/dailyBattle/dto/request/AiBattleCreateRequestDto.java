package com.cosmos.cosmos_backend.dailyBattle.dto.request;

import com.cosmos.cosmos_backend.common.Category;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiBattleCreateRequestDto(

        @Schema(description = "배틀 문제의 알고리즘 카테고리", requiredMode = Schema.RequiredMode.REQUIRED, example = "DP")
        @NotNull
        Category category,

        @Schema(description = "배틀 문제 제목", requiredMode = Schema.RequiredMode.REQUIRED, example = "최단 경로 찾기")
        @NotBlank
        String problemTitle,

        @Schema(description = "배틀 문제 설명", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String problemDescription,

        @Schema(description = "정확히 3개가 필요한 공개 테스트 케이스", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @JsonProperty("testCases")
        @Size(min = 3, max = 3)
        List<@Valid AiBattleCaseCreateRequestDto> cases
) {
        public record AiBattleCaseCreateRequestDto(

                @Schema(description = "테스트 케이스 입력", requiredMode = Schema.RequiredMode.REQUIRED, example = "1 2")
                @NotBlank
                String input,

                @Schema(description = "테스트 케이스 예상 출력", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
                @NotBlank
                String output
        ) {
        }
}
