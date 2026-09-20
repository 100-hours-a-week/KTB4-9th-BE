package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;

/** result는 AI 평가 연동 전까지 카테고리 정답 여부(isCorrect)만 채움. */
public record ApproachSubmitResponse(
        Long submissionId,
        Long problemId,
        String evaluationStatus,
        Result result
) {

    public record Result(Boolean isCorrect) {
    }

    public static ApproachSubmitResponse of(ApproachSubmission submission) {
        return new ApproachSubmitResponse(
                submission.getId(),
                submission.getProblemId(),
                submission.getEvaluationStatus().name(),
                new Result(submission.getCategoryResult())
        );
    }
}
