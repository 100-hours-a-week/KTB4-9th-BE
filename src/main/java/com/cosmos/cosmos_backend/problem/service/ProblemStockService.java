package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemStockResponse;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemStockService {

    private final ProblemRepository problemRepository;

    /** 아무도 풀지 않은 문제 수를 난이도·카테고리 모든 조합별로 조회 (없는 조합은 0). */
    @Transactional(readOnly = true)
    public ProblemStockResponse getUnsolvedProblemCounts() {
        // 1. 제출 기록이 없는 문제를 난이도·카테고리별로 센 결과를 조회
        Map<String, Long> counts = problemRepository.countUnsolvedGroupByDifficultyAndCategory().stream()
                .collect(Collectors.toMap(row -> key(row.getDifficulty(), row.getCategory()), row -> row.getProblemCount()));

        // 2. 모든 조합을 순서대로 만들고, 결과에 없는 조합은 0으로 채움
        List<ProblemStockResponse.ProblemCount> problemCounts = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            for (Category category : Category.values()) {
                problemCounts.add(new ProblemStockResponse.ProblemCount(
                        difficulty, category, counts.getOrDefault(key(difficulty.name(), category.name()), 0L)));
            }
        }
        return new ProblemStockResponse(problemCounts);
    }

    private String key(String difficulty, String category) {
        return difficulty + "|" + category;
    }
}
