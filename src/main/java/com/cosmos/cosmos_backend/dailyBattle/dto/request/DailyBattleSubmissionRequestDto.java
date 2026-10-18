package com.cosmos.cosmos_backend.dailyBattle.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.JoinColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DailyBattleSubmissionRequestDto(

        @NotNull(message = "battle_id_is_required")
        Long battleId,

        @NotNull(message = "answers_is_required")
        @Size(min = 3, max = 3, message = "answers_is_requied_exactly_three")
        List<@Valid Answer> userAnswers
) {
        public record Answer(

                @NotNull(message = "case_number_is_required")
                @JsonProperty("battle_case_num")
                Integer caseNum,
                @NotBlank(message = "answer_is_required")
                @JsonProperty("submitted_output")
                String userAnswer

        ) {
        }
}
