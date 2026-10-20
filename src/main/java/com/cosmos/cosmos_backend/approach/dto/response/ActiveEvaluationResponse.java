package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import java.time.Instant;

/** 문제 상세 조회에 담기는, 현재 진행 중인 자연어 풀이 평가 작업. */
public record ActiveEvaluationResponse(
        Long evaluationTaskId,
        String status,
        String selectedCategory,
        String naturalSolution,
        Instant createdAt,
        Instant startedAt,
        Instant nextRetryAt
) {

    public static ActiveEvaluationResponse from(SolutionEvaluationTask task) {
        return new ActiveEvaluationResponse(
                task.getId(),
                task.getStatus().name(),
                task.getSelectedCategory().name(),
                task.getNaturalSolution(),
                UtcInstants.of(task.getCreatedAt()),
                UtcInstants.of(task.getStartedAt()),
                UtcInstants.of(task.getNextRetryAt())
        );
    }
}
