package com.cosmos.cosmos_backend.approach.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** AI 서버 POST /api/llm/evaluation 응답 본문 (검증 전 원본). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiEvaluationResponse(
        Boolean success,
        String message,
        Integer score,
        String llmFeedback,
        List<Keyword> keywords
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keyword(String keyword, Boolean isIncluded) {
    }
}
