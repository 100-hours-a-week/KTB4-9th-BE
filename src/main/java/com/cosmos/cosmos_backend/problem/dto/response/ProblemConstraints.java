package com.cosmos.cosmos_backend.problem.dto.response;

import java.util.List;

/** problems.constraints 컬럼(JSON)을 파싱한 결과. */
public record ProblemConstraints(
        String inputFormat,
        String outputFormat,
        List<ProblemDetailResponse.InputConstraint> inputConstraints
) {
}
