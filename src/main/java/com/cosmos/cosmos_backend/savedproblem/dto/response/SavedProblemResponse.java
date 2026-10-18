package com.cosmos.cosmos_backend.savedproblem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblemStatus;
import com.cosmos.cosmos_backend.savedproblem.dto.SavedProblemRow;
import java.time.LocalDateTime;

/** 저장 문제 목록의 한 항목. solution은 내 자연어 풀이이며 제출이 없으면 null. */
public record SavedProblemResponse(
        Long problemId,
        String title,
        Difficulty difficulty,
        Category category,
        LocalDateTime savedAt,
        SavedProblemStatus status,
        String solution
) {

    public static SavedProblemResponse from(SavedProblemRow row) {
        return new SavedProblemResponse(
                row.problemId(),
                row.title(),
                row.difficulty(),
                row.category(),
                row.savedAt(),
                SavedProblemStatus.from(row.isSolved()),
                row.naturalSolution()
        );
    }
}
