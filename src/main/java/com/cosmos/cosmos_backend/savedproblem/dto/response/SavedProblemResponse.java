package com.cosmos.cosmos_backend.savedproblem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import java.time.LocalDateTime;

/** 저장 문제 목록의 한 항목. */
public record SavedProblemResponse(
        Long problemId,
        String title,
        Difficulty difficulty,
        Category category,
        LocalDateTime savedAt
) {
}
