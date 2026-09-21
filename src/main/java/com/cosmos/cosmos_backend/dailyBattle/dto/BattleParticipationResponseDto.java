package com.cosmos.cosmos_backend.dailyBattle.dto;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BattleParticipationResponseDto (
        Long battleId,
        Long participantId,
        LocalDate battleDate,

        String tilte,
        String content,

        ParticipationStatus participationStatus,

        OffsetDateTime startedAt,

        Long remainingSecond,

        List<BattleCaseResponseDto> cases

){
}