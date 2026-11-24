package com.cosmos.cosmos_backend.dailyBattle.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.JoinColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DailyBattleSubmissionRequestDto(

        @NotNull(message = "battle_id_is_required")
        @Schema(description = "제출 대상 배틀 ID", example = "1")
        Long battleId,

        @NotNull(message = "answers_is_required")
        @Size(min = 3, max = 3, message = "answers_is_requied_exactly_three")
        @Schema(description = "정확히 3개의 테스트 케이스별 제출 답안")
        List<@Valid Answer> userAnswers
) {
        public record Answer(

                @NotNull(message = "case_number_is_required")
                @JsonProperty("battle_case_num")
                @Schema(description = "테스트 케이스 번호(1~3)", example = "1")
                Integer caseNum,

                @NotBlank(message = "answer_is_required")
                @JsonProperty("submitted_output")
                @Schema(description = "해당 테스트 케이스에 대한 제출 출력", example = "3")
                String userAnswer

        ) {
        }
}
