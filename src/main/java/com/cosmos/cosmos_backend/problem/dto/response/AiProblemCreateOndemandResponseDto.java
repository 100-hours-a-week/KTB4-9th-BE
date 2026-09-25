package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Datatype;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.Scope;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiProblemCreateOndemandResponseDto(

        boolean success,

        String message,

        ProblemInfo problem
) {

    public record ProblemInfo(
            String problemTitle,
            String problemContent,
            String inputFormat,
            String outputFormat,
            Difficulty requestedDifficulty,
            Difficulty difficulty,
            Category category,
            String categorySelectReason,
            List<String> solutionKeywords,
            List<ProblemExample> problemExamples,
            List<InputConstraint> inputConstraints,
            List<ExecutionLimit> executionLimits,
            List<HiddenTestCase> hiddenTestCases,
            List<HintComment> hintComments,
            List<SolutionCode> solutionCodes
    ) {
    }

    public record ProblemExample(
            String input,
            String output,
            String description
    ) {
    }

    public record InputConstraint(
            String target,
            Scope scope,
            Datatype dataType,
            Float minValue,
            Float maxValue,
            Integer dataCount,
            List<String> specialConditions
    ) {
    }

    public record ExecutionLimit(
            Language language,

            @JsonProperty("time_limit_ms")
            Float timeLimitMs,

            @JsonProperty("memory_limit_kb")
            Integer memoryLimitKb
    ) {
    }

    public record HiddenTestCase(
            String input,
            String output
    ) {
    }

    public record HintComment(
            Language language,
            String comment
    ) {
    }

    public record SolutionCode(
            Language language,
            String code
    ) {
    }
}