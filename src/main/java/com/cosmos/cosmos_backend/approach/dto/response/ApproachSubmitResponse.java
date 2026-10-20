package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import java.util.List;

/** result는 AI 평가가 성공했을 때만 만들어지므로 evaluationStatus는 항상 COMPLETED. */
public record ApproachSubmitResponse(
        Long submissionId,
        Long problemId,
        String evaluationStatus,
        Result result,
        SubmissionUsage submissionUsage
) {

    /** correctCategory·categorySelectReason은 카테고리 정답이거나 제출 한도를 다 썼을 때만 채워짐 (아니면 null). */
    public record Result(Boolean isCorrect, Integer approachScore, List<KeywordJudgement> keywords, String aiFeedback,
                         String correctCategory, String categorySelectReason) {
    }

    public record KeywordJudgement(String keyword, Boolean isIncluded) {
    }

    /** 이 문제에 대한 제출 횟수 사용 현황 (문제별 누적, 날짜별 초기화 없음). */
    public record SubmissionUsage(int limit, int usedCount, int remainingCount) {
    }

    /** 제출 한도 초과(429) 시 error data에 담는 값. */
    public record SubmissionLimitExceededData(Long problemId, int limit, int usedCount, int remainingCount) {
    }

    public static ApproachSubmitResponse of(ApproachSubmitResult result) {
        var submission = result.submission();
        int limit = ApproachSubmission.MAX_SUBMISSION_COUNT;
        int usedCount = submission.getSubmittedCount();

        // 정답이거나 제출 한도를 다 썼을 때만 정답 카테고리와 선정 배경을 공개
        boolean reveal = submission.isCategoryRevealable();
        String correctCategory = reveal ? result.problem().getCategory().name() : null;
        String categorySelectReason = reveal ? result.problem().getCategorySelectReason() : null;
        return new ApproachSubmitResponse(
                submission.getId(),
                submission.getProblemId(),
                submission.getEvaluationStatus().name(),
                new Result(
                        submission.getCategoryResult(),
                        submission.getTotalScore(),
                        result.keywords().stream()
                                .map(keyword -> new KeywordJudgement(keyword.keyword(), keyword.included()))
                                .toList(),
                        submission.getAiFeedback(),
                        correctCategory,
                        categorySelectReason
                ),
                new SubmissionUsage(limit, usedCount, limit - usedCount)
        );
    }
}
