package com.cosmos.cosmos_backend.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record RankingCommonResponseDto(

        @JsonProperty("name")
        String username,

        @JsonProperty("profileImageUrl")
        String userProfileImageUrl,

        Long rank,

        Long point,

        @JsonProperty("total_correct_problem_count")
        Long totalCorrectProblemCount,

        @JsonProperty("current_streak_day")
        Long currentStreakDay

) {
}
