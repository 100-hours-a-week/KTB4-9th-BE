package com.cosmos.cosmos_backend.approach.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** AI 서버가 결과 큐로 보내는 메시지. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationResultMessage(
        Long taskId,
        String status,
        Integer score,
        String llmFeedback,
        List<Keyword> keywords,
        String errorCode,
        String message
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keyword(String keyword, Boolean isIncluded) {
    }

    // status가 SUCCESS일 때만 성공으로 봄 (그 외는 모두 실패로 처리)
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
