package com.cosmos.cosmos_backend.approach.dto;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import java.util.List;

/** Service가 Controller에 돌려주는 값. 키워드 판정은 DB에 저장하지 않아 엔티티에 못 담기 때문에 별도로 들고 있음. */
public record ApproachSubmitResult(
        ApproachSubmission submission,
        List<AiEvaluationResult.KeywordJudgement> keywords
) {
}
