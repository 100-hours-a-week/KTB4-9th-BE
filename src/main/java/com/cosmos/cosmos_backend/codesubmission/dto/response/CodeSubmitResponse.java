package com.cosmos.cosmos_backend.codesubmission.dto.response;

import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingStatus;
import com.cosmos.cosmos_backend.common.Language;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 코드 제출(채점) 응답 data. */
public record CodeSubmitResponse(
        @JsonProperty("problem_id") Long problemId,
        Language language,
        @JsonProperty("judging_status") JudgingStatus judgingStatus,
        @JsonProperty("judging_result") JudgingResult judgingResult,
        @JsonProperty("passed_test_count") Integer passedTestCount,
        @JsonProperty("total_test_count") Integer totalTestCount,
        @JsonProperty("submitted_count") Integer submittedCount,
        @JsonProperty("submitted_at") OffsetDateTime submittedAt,
        @JsonProperty("judged_at") OffsetDateTime judgedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 제출 한도 초과(429) 시 error data에 담는 값. */
    public record SubmissionLimitExceededData(
            @JsonProperty("submitted_count") int submittedCount,
            @JsonProperty("submission_limit") int submissionLimit
    ) {
    }

    /** 저장된 제출을 응답 형태로 조립. 시각은 +09:00 형식. */
    public static CodeSubmitResponse of(CodeSubmission submission) {
        return new CodeSubmitResponse(
                submission.getProblemId(),
                submission.getLanguage(),
                submission.getJudgingStatus(),
                submission.getJudgingResult(),
                submission.getPassedTestCount(),
                submission.getTotalTestCount(),
                submission.getSubmittedCount(),
                toOffset(submission.getSubmittedAt()),
                toOffset(submission.getJudgedAt())
        );
    }

    // 서울 시간대의 LocalDateTime을 오프셋 포함 시각으로 변환 (없으면 null)
    private static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }
}
