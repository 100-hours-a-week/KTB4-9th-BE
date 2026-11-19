package com.cosmos.cosmos_backend.dailyBattle.domain;

import java.time.LocalDate;
import java.util.Map;

public record BattleSubmissionInfo(
        Long battleId,

        LocalDate battleDate,

        Map<Integer, Long> caseIdByCaseNum
) {
}
