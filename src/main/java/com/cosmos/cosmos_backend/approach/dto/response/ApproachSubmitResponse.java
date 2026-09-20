package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;

/** result는 AI 평가 연동 전까지 항상 null. */
public record ApproachSubmitResponse(
        Long submissionId,
        Long problemId,
        String evaluationStatus,
        Object result
) {

    public static ApproachSubmitResponse of(ApproachSubmission submission) {
        return new ApproachSubmitResponse(
                submission.getId(),
                submission.getProblemId(),
                submission.getEvaluationStatus().name(),
                null
        );
    }
}
