package com.cosmos.cosmos_backend.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyRankingResponseDto(

        Long userId,

        @JsonProperty("name")
        String username,

        @JsonProperty("profileImageUrl")
        String userProfileUrl,

        Long rank,

        Long point,

        @JsonProperty("total_correct_problem_count")
        Long totalCorrectProblemCount,

        @JsonProperty("current_streak_day")
        Long currentStreakDay

){
}
