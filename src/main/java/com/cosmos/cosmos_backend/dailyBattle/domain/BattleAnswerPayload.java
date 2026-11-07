package com.cosmos.cosmos_backend.dailyBattle.domain;

import java.util.List;

public record BattleAnswerPayload(

        Long participationId,

        Long battleId,

        List<UserAnswer> userAnswerList
) {
    public record UserAnswer(
            Integer caseNum,
            String answer
    ){
    }
}
