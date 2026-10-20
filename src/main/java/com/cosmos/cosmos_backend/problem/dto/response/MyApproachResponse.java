package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import java.util.List;

/** 내가 마지막으로 제출한 자연어 풀이와 그 평가 결과. 결과 모양은 제출 직후 응답과 같음. */
public record MyApproachResponse(
        Category selectedCategory,
        String naturalSolution,
        String evaluationStatus,
        ApproachSubmitResponse.Result result,
        ApproachSubmitResponse.SubmissionUsage submissionUsage
) {

    public static MyApproachResponse from(ApproachSubmission submission, Problem problem) {
        // 1. 저장해 둔 키워드 판정을 꺼냄 (이전에 제출한 기록은 저장돼 있지 않아 빈 목록)
        List<ApproachSubmitResponse.KeywordJudgement> keywords = submission.getKeywordResults() == null
                ? List.of()
                : submission.getKeywordResults().stream()
                        .map(keyword -> new ApproachSubmitResponse.KeywordJudgement(keyword.keyword(), keyword.isIncluded()))
                        .toList();

        // 2. 제출 직후 응답과 같은 모양으로 조립
        int limit = ApproachSubmission.MAX_SUBMISSION_COUNT;
        int usedCount = submission.getSubmittedCount();

        // 정답이거나 제출 한도를 다 썼을 때만 정답 카테고리와 선정 배경을 공개
        boolean reveal = submission.isCategoryRevealable();
        return new MyApproachResponse(
                submission.getSelectedCategory(),
                submission.getNaturalSolution(),
                submission.getEvaluationStatus().name(),
                new ApproachSubmitResponse.Result(
                        submission.getCategoryResult(),
                        submission.getTotalScore(),
                        keywords,
                        submission.getAiFeedback(),
                        reveal ? problem.getCategory().name() : null,
                        reveal ? problem.getCategorySelectReason() : null
                ),
                new ApproachSubmitResponse.SubmissionUsage(limit, usedCount, limit - usedCount)
        );
    }
}
