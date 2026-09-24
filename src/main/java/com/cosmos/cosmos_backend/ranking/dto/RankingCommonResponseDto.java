package com.cosmos.cosmos_backend.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RankingCommonResponseDto(

        @JsonProperty("name")
        String username,

        @JsonProperty("profileImageUrl")
        String userProfileImageUrl,

        Long rank,

        Long point
) {
}
