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

    @Test
    void findByIdAndUserId_returnsTask_onlyForItsOwner() {
        // Given
        SolutionEvaluationTask saved = repository.saveAndFlush(task(1L, 10L));
        em.clear();

        // When & Then
        assertThat(repository.findByIdAndUserId(saved.getId(), 1L)).map(SolutionEvaluationTask::getId).contains(saved.getId());
        assertThat(repository.findByIdAndUserId(saved.getId(), 2L)).isEmpty();
        assertThat(repository.findByIdAndUserId(-1L, 1L)).isEmpty();
    }

    @Test
    void findFirstByUserIdAndProblemIdOrderByIdDesc_returnsMostRecentTask() {
        // Given: 같은 사용자·문제에서 이전 작업이 끝난 뒤 새 작업이 접수됨
        SolutionEvaluationTask first = repository.saveAndFlush(task(1L, 10L));
        first.startProcessing(NOW);
        first.markSucceeded(501L, NOW);
        repository.saveAndFlush(first);
        SolutionEvaluationTask second = repository.saveAndFlush(task(1L, 10L));
        repository.saveAndFlush(task(1L, 11L));
        repository.saveAndFlush(task(2L, 10L));
        em.clear();

        // When
        var latest = repository.findFirstByUserIdAndProblemIdOrderByIdDesc(1L, 10L);

        // Then
        assertThat(latest).map(SolutionEvaluationTask::getId).contains(second.getId());
        assertThat(repository.findFirstByUserIdAndProblemIdOrderByIdDesc(9L, 10L)).isEmpty();
    }

    @Test
    void findNextForUpdate_returnsOldestPendingTask() {
        // Given
        repository.saveAndFlush(new SolutionEvaluationTask(1L, 10L, Category.ARRAY, "나중에 접수", NOW.plusSeconds(10)));
        SolutionEvaluationTask older = repository.saveAndFlush(new SolutionEvaluationTask(2L, 10L, Category.ARRAY, "먼저 접수", NOW));
        em.clear();

        // When
        var next = repository.findNextForUpdate(NOW);

        // Then
        assertThat(next).map(SolutionEvaluationTask::getId).contains(older.getId());
    }

    @Test
    void findNextForUpdate_ignoresTasksThatAreNotPending() {
        // Given
        SolutionEvaluationTask processing = repository.saveAndFlush(task(1L, 10L));
        processing.startProcessing(NOW);
        repository.saveAndFlush(processing);
        SolutionEvaluationTask failed = repository.saveAndFlush(task(2L, 10L));
        failed.startProcessing(NOW);
        failed.markFailed("AI_SERVER_UNAVAILABLE", "최종 실패", NOW);
        repository.saveAndFlush(failed);
        em.clear();

        // When & Then
        assertThat(repository.findNextForUpdate(NOW)).isEmpty();
    }

    @Test
    void findNextForUpdate_returnsEmpty_whenNoTasks() {
        // When & Then
        assertThat(repository.findNextForUpdate(NOW)).isEmpty();
    }

    // 재시도 대기 상태로 저장한 작업을 만듦
    private SolutionEvaluationTask retryWaitTask(Long userId, LocalDateTime createdAt, LocalDateTime nextRetryAt) {
        SolutionEvaluationTask task = repository.saveAndFlush(new SolutionEvaluationTask(userId, 10L, Category.ARRAY, "풀이", createdAt));
        task.startProcessing(createdAt);
        task.scheduleRetry(nextRetryAt, "AI_SERVER_UNAVAILABLE", "ai_server_unavailable", createdAt);
        return repository.saveAndFlush(task);
    }

    @Test
    void findNextForUpdate_returnsRetryWaitTask_whenRetryTimeHasPassed() {
        // Given
        SolutionEvaluationTask due = retryWaitTask(1L, NOW.minusMinutes(1), NOW.minusSeconds(1));
        em.clear();

        // When
        var next = repository.findNextForUpdate(NOW);

        // Then
        assertThat(next).map(SolutionEvaluationTask::getId).contains(due.getId());
    }

    @Test
    void findNextForUpdate_ignoresRetryWaitTask_whenRetryTimeHasNotPassed() {
        // Given
        retryWaitTask(1L, NOW.minusMinutes(1), NOW.plusSeconds(10));
        em.clear();

        // When & Then
        assertThat(repository.findNextForUpdate(NOW)).isEmpty();
    }

    @Test
    void findNextForUpdate_picksOldestAmongPendingAndDueRetry() {
        // Given
        retryWaitTask(1L, NOW.minusMinutes(5), NOW.minusSeconds(1));
        repository.saveAndFlush(new SolutionEvaluationTask(2L, 10L, Category.ARRAY, "더 늦게 접수", NOW.minusMinutes(1)));
        em.clear();

        // When
        var next = repository.findNextForUpdate(NOW);

        // Then
        assertThat(next).map(SolutionEvaluationTask::getUserId).contains(1L);
    }

    @Test
    void findForUpdateById_returnsTask() {
        // Given
        SolutionEvaluationTask saved = repository.saveAndFlush(task(1L, 10L));
        em.clear();

        // When & Then
        assertThat(repository.findForUpdateById(saved.getId())).map(SolutionEvaluationTask::getId).contains(saved.getId());
        assertThat(repository.findForUpdateById(-1L)).isEmpty();
    }

    @Test
    void findStuckIds_returnsOnlyProcessingTasksStartedBeforeThreshold() {
        // Given
        SolutionEvaluationTask stuck = repository.saveAndFlush(task(1L, 10L));
        stuck.startProcessing(NOW.minusMinutes(5));
        repository.saveAndFlush(stuck);

        SolutionEvaluationTask recent = repository.saveAndFlush(task(2L, 10L));
        recent.startProcessing(NOW.minusSeconds(30));
        repository.saveAndFlush(recent);

        SolutionEvaluationTask pending = repository.saveAndFlush(task(3L, 10L));

        SolutionEvaluationTask finished = repository.saveAndFlush(task(4L, 10L));
        finished.startProcessing(NOW.minusMinutes(5));
        finished.markSucceeded(501L, NOW.minusMinutes(4));
        repository.saveAndFlush(finished);
        em.clear();

        // When
        var ids = repository.findStuckIds(NOW.minusMinutes(2));

        // Then
        assertThat(ids).containsExactly(stuck.getId());
        assertThat(ids).doesNotContain(recent.getId(), pending.getId(), finished.getId());
    }

    @Test
    void existsByUserIdAndProblemId_isTrueOnlyForMyTaskOnThatProblem() {
        // Given: 사용자 1이 문제 10에 접수한 작업만 있음 (끝난 작업도 포함됨)
        SolutionEvaluationTask finished = repository.saveAndFlush(task(1L, 10L));
        finished.startProcessing(NOW);
        finished.markSucceeded(501L, NOW);
        repository.saveAndFlush(finished);

        // When & Then
        assertThat(repository.existsByUserIdAndProblemId(1L, 10L)).isTrue();
        assertThat(repository.existsByUserIdAndProblemId(1L, 11L)).isFalse();
        assertThat(repository.existsByUserIdAndProblemId(2L, 10L)).isFalse();
    }
}
