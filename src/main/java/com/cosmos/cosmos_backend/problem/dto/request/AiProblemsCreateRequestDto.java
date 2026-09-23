package com.cosmos.cosmos_backend.problem.dto.request;

import com.cosmos.cosmos_backend.common.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiProblemsCreateRequestDto(

        @JsonProperty("problemCount")
        Long problemCount,

        @JsonProperty("problems")
        @NotNull
        @Valid
        @Size(min = 1, max = 255)
        List<ProblemsInfo> aiProblems

) {

    public record ProblemsInfo(

            @JsonProperty("problemTitle")
            String problemTitle,

            @JsonProperty("problemDescription")
            String problemDescription,

            @JsonProperty("inputFormat")
            String inputFormat,

            @JsonProperty("outputFormat")
            String outputFormat,

            @JsonProperty("difficulty")
            Difficulty difficulty,

            @JsonProperty("category")
            Category category,

            @JsonProperty("categorySelectReason")
            String categorySelectReason,

            @JsonProperty("solutionKeywords")
            List<String> solutionKeywords,

            @JsonProperty("problemExamples")
            List<ProblemExamples> problemExamples,

            @JsonProperty("inputConstraints")
            List<InputConstraints> inputConstraints,

            @JsonProperty("executionLimits")
            @Valid
            @Size(min = 4, max = 4)
            List<ExecutionLimits> executionLimit,

            @JsonProperty("hiddenTestCases")
            List<HiddenTestCases> hiddenTests,

            @JsonProperty("hintComments")
            @Valid
            @Size(min = 4, max = 4)
            List<HintComments> hintComments,

            @JsonProperty("hintSolutionCodes")
            @Valid
            @Size(min = 4, max = 4)
            List<HintSolutionCodes> hintSolutionCodes

    ) {}

    public record ProblemExamples(
            String input,
            String output,
            String description
    ) {}

    public record InputConstraints(
            String target,
            Scope scope,
            Datatype dataType,
            Float minValue,
            Float maxValue,
            List<String> specialConditions
    ) {}

    public record ExecutionLimits(
            Language language,
            Float timeLimitMs,
            Integer memoryLimitKb
    ) {}

    public record HiddenTestCases(
            String input,
            String output
    ) {}

    public record HintComments(
            Language language,
            String content
    ) {}

    public record HintSolutionCodes(
            Language language,
            String content
    ) {}
}