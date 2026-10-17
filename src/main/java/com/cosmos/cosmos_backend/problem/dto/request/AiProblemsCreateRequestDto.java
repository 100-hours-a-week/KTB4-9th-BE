package com.cosmos.cosmos_backend.problem.dto.request;

import com.cosmos.cosmos_backend.common.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiProblemsCreateRequestDto(

        @Schema(description = "전달하는 문제 수. 현재 validation상 선택값입니다.", example = "1")
        @JsonProperty("problemCount")
        Long problemCount,

        @Schema(description = "저장할 문제 목록. 1~255개가 필요합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("problems")
        @NotEmpty
        @Valid
        @Size(min = 1, max = 255)
        List<ProblemsInfo> aiProblems

) {

    public record ProblemsInfo(

            @Schema(description = "문제 제목")
            @JsonProperty("problemTitle")
            @NotBlank
            String problemTitle,

            @Schema(description = "문제 본문")
            @JsonProperty("problemDescription")
            @NotBlank
            String problemDescription,

            @Schema(description = "입력 형식")
            @JsonProperty("inputFormat")
            @NotBlank
            String inputFormat,

            @Schema(description = "출력 형식")
            @JsonProperty("outputFormat")
            @NotBlank
            String outputFormat,

            @Schema(description = "문제 난이도", example = "LV3")
            @JsonProperty("difficulty")
            @NotNull
            Difficulty difficulty,

            @Schema(description = "알고리즘 카테고리", example = "DP")
            @JsonProperty("category")
            @NotNull
            Category category,

            @Schema(description = "카테고리 선정 이유")
            @JsonProperty("categorySelectReason")
            @NotBlank
            String categorySelectReason,

            @Schema(description = "풀이 평가에 사용할 핵심 키워드 목록")
            @JsonProperty("solutionKeywords")
            @Size(min = 1, max = 20)
            @NotEmpty
            List<String> solutionKeywords,

            @Schema(description = "공개 입출력 예제 목록")
            @JsonProperty("problemExamples")
            @Size(min = 1, max = 3)
            @NotEmpty
            List<ProblemExamples> problemExamples,

            @Schema(description = "입력값 제약 조건 목록")
            @JsonProperty("inputConstraints")
            @NotEmpty
            List<InputConstraints> inputConstraints,

            @Schema(description = "언어별 실행 제한. 값이 있다면 4개여야 합니다.")
            @JsonProperty("executionLimits")
            @Valid
            @Size(min = 4, max = 4)
            @NotEmpty
            List<ExecutionLimits> executionLimit,

            @Schema(description = "채점에 사용하는 비공개 테스트 케이스")
            @JsonProperty("hiddenTestCases")
            @Size(min = 1, max = 10)
            @NotEmpty
            List<HiddenTestCases> hiddenTests,

            @Schema(description = "언어별 주석 힌트. 값이 있다면 4개여야 합니다.")
            @JsonProperty("hintComments")
            @Valid
            @Size(min = 4, max = 4)
            @NotEmpty
            List<HintComments> hintComments,

            @Schema(description = "언어별 정답 코드 힌트. 값이 있다면 4개여야 합니다.")
            @JsonProperty("hintSolutionCodes")
            @Valid
            @Size(min = 4, max = 4)
            @NotEmpty
            List<HintSolutionCodes> hintSolutionCodes

    ) {}

    public record ProblemExamples(
            @Schema(description = "예제 입력")
            String input,
            @Schema(description = "예제 출력")
            String output,
            @Schema(description = "예제 설명")
            String description
    ) {}

    public record InputConstraints(
            @Schema(description = "제약 조건 대상")
            String target,
            @Schema(description = "제약 조건 범위")
            Scope scope,
            @Schema(description = "데이터 타입")
            Datatype dataType,
            @Schema(description = "최솟값")
            Float minValue,
            @Schema(description = "최댓값")
            Float maxValue,
            @Schema(description = "추가 조건 목록")
            List<String> specialConditions
    ) {}

    public record ExecutionLimits(
            @Schema(description = "프로그래밍 언어")
            Language language,
            @Schema(description = "실행 시간 제한(ms)")
            Float timeLimitMs,
            @Schema(description = "메모리 제한(KB)")
            Integer memoryLimitKb
    ) {}

    public record HiddenTestCases(
            @Schema(description = "비공개 테스트 입력")
            String input,
            @Schema(description = "비공개 테스트 예상 출력")
            String output
    ) {}

    public record HintComments(
            @Schema(description = "프로그래밍 언어")
            Language language,
            @Schema(description = "주석 힌트 내용")
            @NotBlank
            String content
    ) {}

    public record HintSolutionCodes(
            @Schema(description = "프로그래밍 언어")
            Language language,
            @Schema(description = "정답 코드 힌트")
            @NotBlank
            String content
    ) {}
}
