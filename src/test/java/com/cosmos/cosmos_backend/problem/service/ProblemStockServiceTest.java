package com.cosmos.cosmos_backend.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemStockResponse;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemStockServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    private ProblemStockService service() {
        return new ProblemStockService(problemRepository);
    }

    private ProblemRepository.ProblemCountRow row(String difficulty, String category, long count) {
        return new ProblemRepository.ProblemCountRow() {
            @Override
            public String getDifficulty() {
                return difficulty;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public Long getProblemCount() {
                return count;
            }
        };
    }

    private long countOf(ProblemStockResponse response, Difficulty difficulty, Category category) {
        return response.problemCounts().stream()
                .filter(item -> item.difficulty() == difficulty && item.category() == category)
                .findFirst().orElseThrow().count();
    }

    @Test
    void getUnsolvedProblemCounts_fillsEveryCombinationInOrder_withZeroForMissing() {
        // Given
        when(problemRepository.countUnsolvedGroupByDifficultyAndCategory()).thenReturn(List.of(
                row("LV2", "DP", 3), row("LV3", "ARRAY", 2), row("LV1", "GREEDY", 7)
        ));

        // When
        ProblemStockResponse response = service().getUnsolvedProblemCounts();

        // Then
        assertThat(response.problemCounts()).hasSize(Difficulty.values().length * Category.values().length);
        assertThat(countOf(response, Difficulty.LV2, Category.DP)).isEqualTo(3);
        assertThat(countOf(response, Difficulty.LV3, Category.ARRAY)).isEqualTo(2);
        assertThat(countOf(response, Difficulty.LV1, Category.GREEDY)).isEqualTo(7);
        assertThat(countOf(response, Difficulty.LV5, Category.MATH)).isZero();
        assertThat(response.problemCounts().get(0).difficulty()).isEqualTo(Difficulty.LV1);
        assertThat(response.problemCounts().get(0).category()).isEqualTo(Category.ARRAY);
        var last = response.problemCounts().get(response.problemCounts().size() - 1);
        assertThat(last.difficulty()).isEqualTo(Difficulty.LV5);
        assertThat(last.category()).isEqualTo(Category.MATH);
    }

    @Test
    void getUnsolvedProblemCounts_returnsAllZero_whenNoUnsolvedRows() {
        // Given
        when(problemRepository.countUnsolvedGroupByDifficultyAndCategory()).thenReturn(List.of());

        // When
        ProblemStockResponse response = service().getUnsolvedProblemCounts();

        // Then
        assertThat(response.problemCounts()).hasSize(80);
        assertThat(response.problemCounts()).allMatch(item -> item.count() == 0);
    }
}
