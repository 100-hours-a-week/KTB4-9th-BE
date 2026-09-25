package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import java.util.List;

/** 문제 상세 조회 응답. */
public record ProblemDetailResponse(
        Long problemId,
        Difficulty difficulty,
        Category category,
        String title,
        String content,
        String inputFormat,
        String outputFormat,
        List<AiProblemsCreateRequestDto.InputConstraints> inputConstraints,
        List<AiProblemsCreateRequestDto.ExecutionLimits> executionLimits,
        List<AiProblemsCreateRequestDto.ProblemExamples> examples,
        // 힌트 사용 단계 (0: 안 씀, 1: 주석 힌트까지, 2: 정답 힌트까지)
        Integer usedHintStage
) {


    /** 엔티티들을 응답 형태로 조립. */
    public static ProblemDetailResponse of(
            Problem problem,
            List<ProblemExample> examples,
            List<RunningLimit> runningLimits,
            int usedHintStage
    ) {
        return new ProblemDetailResponse(
                // 1. 문제 기본 정보
                problem.getId(),
                problem.getDifficulty(),
                problem.getCategory(),
                problem.getTitle(),
                problem.getContent(),
                // 2. 입력/출력 형식(별도 컬럼)과 입력 제한(constraints JSON)
                problem.getInputFormat(),
                problem.getOutputFormat(),
                problem.getConstraints(),
                // 3. 언어별 실행 제한
                runningLimits.stream()
                        .map(limit -> new AiProblemsCreateRequestDto.ExecutionLimits(
                                limit.getLanguage(),
                                limit.getTimeLimitMs(),
                                limit.getMemoryLimitMb() * 1024 // MB → KB 변환
                        ))
                        .toList(),
                // 4. 공개 예시
                examples.stream()
                        .map(e -> new AiProblemsCreateRequestDto.ProblemExamples(e.getInput(), e.getOutput(), e.getDescription()))
                        .toList(),
                // 5. 힌트 사용 단계
                usedHintStage
        );
    }
}
