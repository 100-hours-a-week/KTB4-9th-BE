package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 문제 생성(선택) 응답 data. */
public record ProblemSelectionResponse(ProblemSummary problem, DailyUsage dailyUsage) {

    public record ProblemSummary(
            Long problemId,
            Integer level,
            Category category,
            String title,
            String content,
            List<AiProblemsCreateRequestDto.ProblemExamples> examples
    ) {}

    public record DailyUsage(LocalDate date, int limit, int usedCount, int remainingCount) {}

    /** 429일 때 error data에 담는 값 (성공 응답과 달리 resetAt이 있음). */
    public record DailyLimitExceededData(
            LocalDate date,
            int limit,
            int usedCount,
            int remainingCount,
            OffsetDateTime resetAt
    ) {}

    /** 엔티티들을 명세 모양으로 조립. */
    public static ProblemSelectionResponse of(
            Problem problem,
            List<ProblemExample> examples,
            LocalDate date,
            int usedCount,
            int limit
    ) {
        return new ProblemSelectionResponse(
                new ProblemSummary(
                        problem.getId(),
                        // "LV3" → 3 (명세의 level은 숫자)
                        Integer.parseInt(problem.getDifficulty().name().substring(2)),
                        problem.getCategory(),
                        problem.getTitle(),
                        problem.getContent(),
                        examples.stream()
                                .map(e -> new AiProblemsCreateRequestDto.ProblemExamples(e.getInput(), e.getOutput(), e.getDescription()))
                                .toList()
                ),
                new DailyUsage(date, limit, usedCount, limit - usedCount)
        );
    }
}
