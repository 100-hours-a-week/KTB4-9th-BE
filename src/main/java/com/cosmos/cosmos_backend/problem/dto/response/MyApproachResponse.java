package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;

/** 내가 마지막으로 제출한 자연어 풀이와 그 평가 결과. 결과 모양은 평가 작업 조회 응답과 같음. */
public record MyApproachResponse(
        Category selectedCategory,
        String naturalSolution,
        // 제출 행은 평가가 성공했을 때만 만들어지므로 항상 COMPLETED (기존 응답 필드를 유지하기 위해 남김)
        String evaluationStatus,
        ApproachSubmitResponse.Result result,
        ApproachSubmitResponse.SubmissionUsage submissionUsage
) {

    private static final String COMPLETED = "COMPLETED";

    public static MyApproachResponse from(ApproachSubmission submission, Problem problem) {
        return new MyApproachResponse(
                submission.getSelectedCategory(),
                submission.getNaturalSolution(),
                COMPLETED,
                ApproachSubmitResponse.Result.from(submission, problem),
                ApproachSubmitResponse.SubmissionUsage.of(submission.getSubmittedCount())
        );
    }
}
