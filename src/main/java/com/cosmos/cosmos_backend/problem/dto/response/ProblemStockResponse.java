package com.cosmos.cosmos_backend.problem.dto.response;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import java.util.List;

/** 아무도 풀지 않은 문제 수 조회 응답 data. */
public record ProblemStockResponse(List<ProblemCount> problemCounts) {

    public record ProblemCount(Difficulty difficulty, Category category, long count) {
    }
}
