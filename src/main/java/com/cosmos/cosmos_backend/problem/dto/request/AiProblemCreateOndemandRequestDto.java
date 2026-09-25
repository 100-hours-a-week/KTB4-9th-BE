package com.cosmos.cosmos_backend.problem.dto.request;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;

public record AiProblemCreateOndemandRequestDto(
        Difficulty difficulty,
        Category category
) {
}
