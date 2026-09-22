package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import java.util.List;

/** result는 AI 평가가 성공했을 때만 만들어지므로 evaluationStatus는 항상 COMPLETED. */
public record ApproachSubmitResponse(
        Long submissionId,
        Long problemId,
        String evaluationStatus,
        Result result
) {

    public record Result(Boolean isCorrect, Integer approachScore, List<KeywordJudgement> keywords, String aiFeedback) {
    }

    public record KeywordJudgement(String keyword, Boolean isIncluded) {
    }

    public static ApproachSubmitResponse of(ApproachSubmitResult result) {
        var submission = result.submission();
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
                        submission.getAiFeedback()
                )
        );
    }
}
