package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import java.time.Instant;

/** 평가 작업 조회 응답. 최신 작업이 아니면 submissionId·result·failure는 null. */
public record SolutionEvaluationResponse(
        Long evaluationTaskId,
        Long problemId,
        String status,
        Boolean isLatest,
        LatestEvaluation latestEvaluation,
        Long submissionId,
        String selectedCategory,
        String naturalSolution,
        ApproachSubmitResponse.Result result,
        Failure failure,
        ApproachSubmitResponse.SubmissionUsage submissionUsage,
        Instant createdAt,
        Instant startedAt,
        Instant nextRetryAt,
        Instant completedAt
) {

    /** 같은 사용자·문제에서 가장 최근에 접수한 작업. */
    public record LatestEvaluation(Long evaluationTaskId, String status) {
    }

    /** 최종 실패 안내. 실패는 제출 횟수를 쓰지 않으므로 항상 다시 제출할 수 있음. */
    public record Failure(String code, String message, boolean canResubmit) {
    }

    /** 조회한 작업, 최신 작업, 결과·실패 안내, 성공한 제출 횟수로 응답을 조립. */
    public static SolutionEvaluationResponse of(SolutionEvaluationTask task, SolutionEvaluationTask latest,
                                                ApproachSubmitResponse.Result result, Failure failure, int usedCount) {
        boolean isLatest = latest.getId().equals(task.getId());
        return new SolutionEvaluationResponse(
                task.getId(),
                task.getProblemId(),
                task.getStatus().name(),
                isLatest,
                new LatestEvaluation(latest.getId(), latest.getStatus().name()),
                // 최신 작업이 아니면 결과 관련 필드는 모두 비움
                isLatest ? task.getSubmissionId() : null,
                task.getSelectedCategory().name(),
                task.getNaturalSolution(),
                isLatest ? result : null,
                isLatest ? failure : null,
                ApproachSubmitResponse.SubmissionUsage.of(usedCount),
                UtcInstants.of(task.getCreatedAt()),
                UtcInstants.of(task.getStartedAt()),
                UtcInstants.of(task.getNextRetryAt()),
                UtcInstants.of(task.getCompletedAt())
        );
    }
}
