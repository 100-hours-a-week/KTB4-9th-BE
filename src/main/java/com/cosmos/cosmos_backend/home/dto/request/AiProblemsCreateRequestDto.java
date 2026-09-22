package com.cosmos.cosmos_backend.home.dto.request;

import com.cosmos.cosmos_backend.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiProblemsCreateRequestDto(

        @Size(min = 5, max = 5)
        @Valid
        @NotNull
        List<AiProblemRequestDto> aiProblems

) {
    public record AiProblemRequestDto(

            Long problemCount,

            ProblemsInfo problemsInfo

    ) {
    }

    public record ProblemsInfo(
            String problemTitle,
            String problemDescription,
            String inputFormat,
            String outputFormat,
            Difficulty difficulty,
            Category category,
            String categorySelectReason,
            List<String> solutionKeywords,
            List<ProblemExamples> problemExamples,
            List<InputConstraints> inputConstraints,
            List<ExecutionLimits> executionLimit,
            List<HiddenTestCases> hiddenTests,
            List<HintComments> hintComments,
            List<HintSolutionCodes> hintSolutionCodes
    ){
    }

    public record ProblemExamples(
            String input,
            String output,
            String description
    ){
    }

    public record InputConstraints(
            String target,
            Scope scope,
            Datatype dataType,
            Float minValue,
            Float maxValue,
            List<String> specialConditions
    ){
    }

    public record ExecutionLimits(
            Language language,
            Float timeLimitMs,
            Integer memoryLimitKb
    ){
    }

    public record HiddenTestCases(
            String input,
            String output
    ){
    }

    public record HintComments(
            Language language,
            String content
    ){
    }

    public record HintSolutionCodes(
            Language language,
            String content
    ){}

}
