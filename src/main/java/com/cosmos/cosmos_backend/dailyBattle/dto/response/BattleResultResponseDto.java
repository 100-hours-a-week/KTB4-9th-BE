package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record BattleResultResponseDto(

        @JsonProperty("battle_id")
        Long battleId,

        @JsonProperty("battle_date")
        LocalDate battleDate,

        @JsonProperty("title")
        String battleTitle,

        @JsonProperty("my_result")
        MyResult myResult,

        @JsonProperty("top5_rankings")
        List<TopRanking> top5Rankings

) {
    public record MyResult(
            @JsonProperty("participation_status")
            ParticipationStatus  participationStatus,

            @JsonProperty("my_rank")
            Long rank,

            @JsonProperty("elapsed_seconds")
            Long elapsedSeconds,

            @JsonProperty("all_correct")
            Boolean allCorrect,

            @JsonProperty("answers")
            List<MyAnswer> myAnswers
    ){
        public record MyAnswer(
                @JsonProperty("battle_case_id")
                Long battleCaseId,

                @JsonProperty("display_order")
                Integer displayOrder,

                String input,

                @JsonProperty("submitted_output")
                String submittedOutput,

                @JsonProperty("expected_output")
                String expectedOutput,

                @JsonProperty("is_correct")
                Boolean isCorrect
        ){}
    }

    public record TopRanking(
            Long rank,

            @JsonProperty("user_id")
            Long userId,

            @JsonProperty("name")
            String userName,

            @JsonProperty("profile_image_url")
            String profileImageUrl,

            @JsonProperty("elapsed_seconds")
            Long elapsedSeconds
    ){}
}
