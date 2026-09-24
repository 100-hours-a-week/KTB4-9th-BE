package com.cosmos.cosmos_backend.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyRankingResponseDto(

        Long userId,

        @JsonProperty("name")
        String username,

        String userProfileUrl,

        Long rank,

        Long point

){
}
