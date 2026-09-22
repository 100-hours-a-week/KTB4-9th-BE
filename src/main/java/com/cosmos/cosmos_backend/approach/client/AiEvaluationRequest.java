package com.cosmos.cosmos_backend.approach.client;

import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import java.util.List;

/** AI 서버 POST /api/llm/evaluation 요청 본문. */
public record AiEvaluationRequest(
        String problemTitle,
        String problemDescription,
        String category,
        String categorySelectReason,
        List<String> solutionKeywords,
        List<ProblemDetailResponse.InputConstraint> inputConstraints,
        List<ProblemDetailResponse.ExecutionLimit> executionLimits,
        String naturalSolution
) {
}
