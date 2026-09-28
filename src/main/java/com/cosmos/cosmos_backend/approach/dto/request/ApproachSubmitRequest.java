package com.cosmos.cosmos_backend.approach.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ApproachSubmitRequest(
        @Schema(description = "사용자가 선택한 알고리즘 카테고리", allowableValues = {"ARRAY", "STRING", "DP", "GRAPH", "TREE", "STACK_QUEUE", "BINARY_SEARCH", "GREEDY", "BACKTRACKING", "TWO_POINTER", "HASH", "HEAP", "SORTING", "IMPLEMENTATION", "BRUTE_FORCE", "MATH"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "DP")
        @NotBlank(message = "selected_category_is_required") String selectedCategory,

        @Schema(description = "문제 해결 방법을 설명한 자연어 풀이", requiredMode = Schema.RequiredMode.REQUIRED, example = "이전 단계까지의 최적값을 저장해 중복 계산을 제거합니다.")
        @JsonProperty("natural_solution") @NotBlank(message = "approach_is_required") String naturalSolution
) {
}
