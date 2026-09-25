package com.cosmos.cosmos_backend.approach.client;

import java.util.List;

/** 검증을 통과한 AI 평가 결과. */
public record AiEvaluationResult(
        int score,
        String feedback,
        List<KeywordJudgement> keywords
) {

    public record KeywordJudgement(String keyword, boolean included) {
    }
}
