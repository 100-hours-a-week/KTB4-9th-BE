package com.cosmos.cosmos_backend.dailyBattle.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BattleParticipationResponseDto (
        @Schema(description = "문제의 알고리즘 카테고리")
        Category category,

        @Schema(description = "참여한 배틀 ID", example = "1")
        Long battleId,

        @Schema(description = "배틀에 참여한 사용자 ID", example = "1")
        Long participantId,

        @Schema(description = "배틀 진행 날짜")
        LocalDate battleDate,

        @Schema(description = "배틀 문제 제목")
        String tilte,

        @Schema(description = "배틀 문제 내용")
        String content,

        @Schema(description = "현재 배틀 참여 상태", example = "IN_PROGRESS")
        ParticipationStatus participationStatus,

        @Schema(description = "배틀 종료까지 남은 시간(초)", example = "300")
        Long remainingSecond,

        @Schema(description = "공개 테스트 케이스 목록")
        List<BattleCaseResponseDto> cases

){
}
