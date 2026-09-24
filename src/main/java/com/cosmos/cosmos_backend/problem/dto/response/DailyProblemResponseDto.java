package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;

import java.time.LocalDate;
import java.util.List;

public record DailyProblemResponseDto(
        LocalDate recommendDate,

        List<DailyProblems> dailyProblem
) {

    public record DailyProblems(
            Long problemId,
            Difficulty difficulty,
            Category category,
            String problemTitle,
            String problemContent,
            List<AiProblemsCreateRequestDto.ProblemExamples> examples
    ){
    }
}
