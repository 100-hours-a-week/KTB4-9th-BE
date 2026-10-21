package com.cosmos.cosmos_backend.approach.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import java.util.List;

/** 평가 결과와 제출 사용 현황처럼 여러 응답에서 함께 쓰는 조각 모음. */
public final class ApproachSubmitResponse {

    private ApproachSubmitResponse() {
    }

    /** correctCategory·categorySelectReason은 카테고리 정답이거나 제출 한도를 다 썼을 때만 채워짐 (아니면 null). */
    public record Result(Boolean isCorrect, Integer approachScore, List<KeywordJudgement> keywords, String aiFeedback,
                         String correctCategory, String categorySelectReason) {

        /** 제출 행과 문제로 평가 결과를 조립. */
        public static Result from(ApproachSubmission submission, Problem problem) {
            // 1. 저장해 둔 키워드 판정을 꺼냄 (이전에 제출한 기록은 저장돼 있지 않아 빈 목록)
            List<KeywordJudgement> keywords = submission.getKeywordResults() == null
                    ? List.of()
                    : submission.getKeywordResults().stream()
                            .map(keyword -> new KeywordJudgement(keyword.keyword(), keyword.isIncluded()))
                            .toList();

            // 2. 정답이거나 제출 한도를 다 썼을 때만 정답 카테고리와 선정 배경을 공개
            boolean reveal = submission.isCategoryRevealable();
            return new Result(
                    submission.getCategoryResult(),
                    submission.getTotalScore(),
                    keywords,
                    submission.getAiFeedback(),
                    reveal ? problem.getCategory().name() : null,
                    reveal ? problem.getCategorySelectReason() : null
            );
        }
    }

    public record KeywordJudgement(String keyword, Boolean isIncluded) {
    }

    /** 이 문제에 대한 제출 횟수 사용 현황 (문제별 누적, 날짜별 초기화 없음). */
    public record SubmissionUsage(int limit, int usedCount, int remainingCount) {

        /** 성공한 제출 횟수로 사용 현황을 조립. */
        public static SubmissionUsage of(int usedCount) {
            int limit = ApproachSubmission.MAX_SUBMISSION_COUNT;
            return new SubmissionUsage(limit, usedCount, limit - usedCount);
        }
    }

    /** 제출 한도 초과(429) 시 error data에 담는 값. */
    public record SubmissionLimitExceededData(Long problemId, SubmissionUsage submissionUsage) {
    }
}
