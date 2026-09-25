package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 힌트 조회 응답 data. */
public record HintResponse(
        @JsonProperty("problem_id") Long problemId,
        @JsonProperty("hint_type") String hintType,
        @JsonProperty("hint_stage") Integer hintStage,
        String content
) {

    /** DB의 힌트 유형(SOLUTION)을 응답 값(ANSWER)으로 바꿔서 조립. */
    public static HintResponse of(Long problemId, HintType hintType, int stage, String content) {
        String type = hintType == HintType.COMMENT ? "COMMENT" : "ANSWER";
        return new HintResponse(problemId, type, stage, content);
    }
}
