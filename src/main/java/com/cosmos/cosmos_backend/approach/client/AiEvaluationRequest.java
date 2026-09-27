package com.cosmos.cosmos_backend.approach.client;

import java.util.List;

/** AI 서버 POST /api/llm/evaluation 요청 본문. */
public record AiEvaluationRequest(
        String problemTitle,
        String problemContent,
        String category,
        String categorySelectReason,
        List<String> solutionKeywords,
        List<InputConstraint> inputConstraints,
        List<ExecutionLimit> executionLimits,
        String naturalSolution
) {

    /** AI 서버가 기대하는 값 형식(scope는 소문자, dataType은 AI 쪽 이름)으로 맞춘 입력 제한. */
    public record InputConstraint(
            String target,
            String scope,
            String dataType,
            Float minValue,
            Float maxValue,
            List<String> specialConditions
    ) {
    }

    /** AI 서버가 기대하는 값 형식(language는 소문자)으로 맞춘 실행 제한. */
    public record ExecutionLimit(
            String language,
            Float timeLimitMs,
            Integer memoryLimitKb
    ) {
    }
}
