package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;

import java.util.List;

/** problems.constraints 컬럼(JSON)의 내용. input_format/output_format은 별도 컬럼으로 분리됨. */
public record ProblemConstraints(
        List<AiProblemsCreateRequestDto.InputConstraints> inputConstraints
) {
}
