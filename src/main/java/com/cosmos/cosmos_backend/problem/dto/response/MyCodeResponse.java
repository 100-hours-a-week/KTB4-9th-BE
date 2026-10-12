package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.common.Language;

/** 내가 마지막으로 제출한 코드와 그 채점 요약. 테스트케이스별 판정은 저장하지 않아 포함하지 않음. */
public record MyCodeResponse(
        Language language,
        String sourceCode,
        JudgingResult judgingResult,
        Integer passedTestCount,
        Integer totalTestCount,
        int submittedCount
) {

    public static MyCodeResponse from(CodeSubmission submission) {
        return new MyCodeResponse(
                submission.getLanguage(),
                submission.getSourceCode(),
                submission.getJudgingResult(),
                submission.getPassedTestCount(),
                submission.getTotalTestCount(),
                submission.getSubmittedCount()
        );
    }
}
