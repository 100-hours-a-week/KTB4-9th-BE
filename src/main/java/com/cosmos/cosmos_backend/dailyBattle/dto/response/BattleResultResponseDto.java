package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record BattleResultResponseDto(

        @JsonProperty("battle_id")
        @Schema(description = "배틀 ID", example = "1")
        Long battleId,

        @JsonProperty("battle_date")
        @Schema(description = "배틀 진행 날짜")
        LocalDate battleDate,

        @JsonProperty("title")
        @Schema(description = "배틀 문제 제목")
        String battleTitle,

        @JsonProperty("my_result")
        @Schema(description = "로그인한 사용자의 결과. 미참여 시 null")
        MyResult myResult,

        @JsonProperty("top5_rankings")
        @Schema(description = "상위 5명 순위")
        List<TopRanking> top5Rankings

) {
    public record MyResult(
            @JsonProperty("participation_status")
            @Schema(description = "최종 배틀 참여 상태", example = "GRADING_COMPLETED")
            ParticipationStatus  participationStatus,

            @JsonProperty("my_rank")
            @Schema(description = "순위. 내 결과에서는 순위가 없으면 null", example = "1")
            Long rank,

            @JsonProperty("elapsed_seconds")
            @Schema(description = "배틀 참여부터 제출까지 소요 시간(초)", example = "120")
            Long elapsedSeconds,

            @JsonProperty("all_correct")
            @Schema(description = "모든 테스트 케이스 정답 여부", example = "true")
            Boolean allCorrect,

            @JsonProperty("answers")
            @Schema(description = "내 테스트 케이스별 채점 결과. 채점 미완료 시 null")
            List<MyAnswer> myAnswers
    ){
        public record MyAnswer(
                @JsonProperty("battle_case_id")
                @Schema(description = "테스트 케이스 ID", example = "1")
                Long battleCaseId,

                @JsonProperty("display_order")
                @Schema(description = "테스트 케이스 표시 순서", example = "1")
                Integer displayOrder,

                @Schema(description = "테스트 케이스 입력", example = "1 2")
                String input,

                @JsonProperty("submitted_output")
                @Schema(description = "사용자가 제출한 출력", example = "3")
                String submittedOutput,

                @JsonProperty("expected_output")
                @Schema(description = "테스트 케이스 정답 출력", example = "3")
                String expectedOutput,

                @JsonProperty("is_correct")
                @Schema(description = "해당 테스트 케이스 정답 여부", example = "true")
                Boolean isCorrect
        ){}
    }

    public record TopRanking(
            @Schema(description = "순위. 내 결과에서는 순위가 없으면 null", example = "1")
            Long rank,

            @JsonProperty("user_id")
            @Schema(description = "사용자 ID", example = "1")
            Long userId,

            @JsonProperty("name")
            @Schema(description = "사용자 이름")
            String userName,

            @JsonProperty("profile_image_url")
            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @JsonProperty("elapsed_seconds")
            @Schema(description = "배틀 참여부터 제출까지 소요 시간(초)", example = "120")
            Long elapsedSeconds
    ){}
}
