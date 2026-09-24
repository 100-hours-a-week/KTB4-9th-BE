package com.cosmos.cosmos_backend.ranking.dto;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RankingFilterResponseDto(

        @JsonProperty("level")
        Difficulty difficulty,

        Category category

) {
}
