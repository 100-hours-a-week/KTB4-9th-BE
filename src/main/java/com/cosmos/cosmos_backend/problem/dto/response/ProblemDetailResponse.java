package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.problem.domain.Problem;
import com.cosmos.cosmos_backend.problem.domain.ProblemExample;
import com.cosmos.cosmos_backend.problem.domain.RunningLimit;
import java.util.List;

/** 문제 상세 조회 응답. */
public record ProblemDetailResponse(
        Long problemId,
        Integer level,
        String category,
        String title,
        String content,
        String inputFormat,
        String outputFormat,
        List<InputConstraint> inputConstraints,
        List<ExecutionLimit> executionLimits,
        List<Example> examples
) {

    /** 입력값 범위/타입 제약. */
    public record InputConstraint(
            String target,
            String scope,
            String dataType,
            Long minValue,
            Long maxValue,
            List<String> specialConditions
    ) {
    }

    /** 언어별 채점 실행 제한. */
    public record ExecutionLimit(String language, Integer timeLimitMs, Integer memoryLimitKb) {
    }

    public record Example(String input, String output, String description) {
    }

    /** 엔티티들을 응답 형태로 조립. */
    public static ProblemDetailResponse of(
            Problem problem,
            ProblemConstraints constraints,
            List<ProblemExample> examples,
            List<RunningLimit> runningLimits
    ) {
        return new ProblemDetailResponse(
                // 1. 문제 기본 정보
                problem.getId(),
                parseLevel(problem.getDifficulty()),
                problem.getCategory(),
                problem.getTitle(),
                problem.getContent(),
                // 2. 입력/출력 형식과 입력 제한 (constraints JSON에서 꺼냄)
                constraints.inputFormat(),
                constraints.outputFormat(),
                constraints.inputConstraints(),
                // 3. 언어별 실행 제한
                runningLimits.stream()
                        .map(limit -> new ExecutionLimit(
                                limit.getLanguage().name(),
                                limit.getTimeLimitMs(),
                                limit.getMemoryLimitMb() * 1024 // MB → KB 변환
                        ))
                        .toList(),
                // 4. 공개 예시
                examples.stream()
                        .map(e -> new Example(e.getInput(), e.getOutput(), e.getDescription()))
                        .toList()
        );
    }

    // "LV1" → 1
    private static Integer parseLevel(String difficulty) {
        return Integer.parseInt(difficulty.substring(2));
    }
}
