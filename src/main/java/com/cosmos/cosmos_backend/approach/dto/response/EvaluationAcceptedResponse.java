package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import java.time.Instant;

/** 풀이 평가를 접수했을 때(202)의 응답. */
public record EvaluationAcceptedResponse(
        Long evaluationTaskId,
        Long problemId,
        String status,
        Instant createdAt,
        ApproachSubmitResponse.SubmissionUsage submissionUsage
) {

    /** 접수한 작업과 지금까지 성공한 제출 횟수로 응답을 조립 (접수로는 횟수가 늘지 않음). */
    public static EvaluationAcceptedResponse of(SolutionEvaluationTask task, int usedCount) {
        return new EvaluationAcceptedResponse(
                task.getId(),
                task.getProblemId(),
                task.getStatus().name(),
                UtcInstants.of(task.getCreatedAt()),
                ApproachSubmitResponse.SubmissionUsage.of(usedCount)
        );
    }

    /** 이미 진행 중인 작업이 있을 때(409) error data에 담는 값. */
    public record InProgressData(Long evaluationTaskId, String status) {
    }
}
