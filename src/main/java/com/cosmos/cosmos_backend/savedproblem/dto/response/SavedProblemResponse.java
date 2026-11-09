package com.cosmos.cosmos_backend.savedproblem.dto.response;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
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
        // 1. 카테고리를 알고 있었거나, 맞췄거나, 제출 한도를 다 썼으면 공개
        boolean limitReached = row.submittedCount() != null
                && row.submittedCount() >= ApproachSubmission.MAX_SUBMISSION_COUNT;
        boolean categoryVisible = Boolean.TRUE.equals(row.categoryKnown())
                || Boolean.TRUE.equals(row.categoryResult())
                || limitReached;

        // 2. 공개 조건이 아니면 카테고리를 null로 내림
        return new SavedProblemResponse(
                row.problemId(),
                row.title(),
                row.difficulty(),
                categoryVisible ? row.category() : null,
                row.savedAt(),
                SavedProblemStatus.from(row.isSolved()),
                row.naturalSolution()
        );
    }
}
