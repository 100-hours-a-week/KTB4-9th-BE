package com.cosmos.cosmos_backend.approach.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.common.Category;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class SolutionEvaluationTaskRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Autowired
    private SolutionEvaluationTaskRepository repository;

    @Autowired
    private TestEntityManager em;

    private SolutionEvaluationTask task(Long userId, Long problemId) {
        return new SolutionEvaluationTask(userId, problemId, Category.BINARY_SEARCH, "중간값을 확인합니다", NOW);
    }

    @Test
    void save_andReload_keepsAllFields() {
        // Given
        SolutionEvaluationTask saved = repository.saveAndFlush(task(1L, 10L));
        em.clear();

        // When
        SolutionEvaluationTask loaded = repository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getProblemId()).isEqualTo(10L);
        assertThat(loaded.getSelectedCategory()).isEqualTo(Category.BINARY_SEARCH);
        assertThat(loaded.getNaturalSolution()).isEqualTo("중간값을 확인합니다");
        assertThat(loaded.getStatus()).isEqualTo(EvaluationTaskStatus.PENDING);
        assertThat(loaded.getWorkerAttemptCount()).isZero();
        assertThat(loaded.getActiveSlot()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void save_throws_whenSameUserAndProblemAlreadyHaveActiveTask() {
        // Given
        repository.saveAndFlush(task(1L, 10L));

        // When & Then
        assertThatThrownBy(() -> repository.saveAndFlush(task(1L, 10L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsNewTask_afterPreviousTaskFinished() {
        // Given
        SolutionEvaluationTask first = repository.saveAndFlush(task(1L, 10L));
        first.startProcessing(NOW);
        first.markSucceeded(501L, NOW);
        repository.saveAndFlush(first);

        // When
        SolutionEvaluationTask second = repository.saveAndFlush(task(1L, 10L));

        // Then
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void save_allowsActiveTaskForDifferentUserOnSameProblem() {
        // Given
        repository.saveAndFlush(task(1L, 10L));

        // When
        SolutionEvaluationTask other = repository.saveAndFlush(task(2L, 10L));

        // Then
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void findActive_returnsActiveTask_andEmptyAfterFailure() {
        // Given
        SolutionEvaluationTask saved = repository.saveAndFlush(task(1L, 10L));

        // When & Then
        assertThat(repository.findActive(1L, 10L)).map(SolutionEvaluationTask::getId).contains(saved.getId());

        saved.startProcessing(NOW);
        saved.markFailed("AI_SERVER_UNAVAILABLE", "최종 실패", NOW);
        repository.saveAndFlush(saved);
        assertThat(repository.findActive(1L, 10L)).isEmpty();
    }
}
