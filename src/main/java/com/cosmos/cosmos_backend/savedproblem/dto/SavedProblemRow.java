package com.cosmos.cosmos_backend.savedproblem.dto;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import java.time.LocalDateTime;

/** 저장 문제 조회 쿼리의 한 줄 결과. 제출이 없으면 isSolved, naturalSolution, categoryResult, submittedCount는 null. */
public record SavedProblemRow(
        Long problemId,
        String title,
        Difficulty difficulty,
        Category category,
        LocalDateTime savedAt,
        Boolean isSolved,
        String naturalSolution,
        Boolean categoryKnown,
        Boolean categoryResult,
        Integer submittedCount
) {
}
