package com.cosmos.cosmos_backend.approach.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.common.Category;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SolutionEvaluationTaskRepositoryRelayTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Autowired
    private SolutionEvaluationTaskRepository repository;

    @Autowired
    private TestEntityManager em;

    // PENDING 작업을 저장 (예약 시각은 선택)
    private SolutionEvaluationTask pending(Long userId, LocalDateTime createdAt, LocalDateTime reservedUntil) {
        SolutionEvaluationTask task = new SolutionEvaluationTask(userId, 10L, Category.ARRAY, "풀이", createdAt);
        if (reservedUntil != null) {
            task.reservePublish(reservedUntil, createdAt);
        }
        return repository.saveAndFlush(task);
    }

    @Test
    void findPublishTargetsForUpdate_returnsPendingWithoutReservation_oldestFirst() {
        // Given
        SolutionEvaluationTask newer = pending(1L, NOW.minusSeconds(1), null);
        SolutionEvaluationTask older = pending(2L, NOW.minusSeconds(5), null);
        em.clear();

        // When
        List<SolutionEvaluationTask> targets = repository.findPublishTargetsForUpdate(NOW, 10);

        // Then
        assertThat(targets).extracting(SolutionEvaluationTask::getId).containsExactly(older.getId(), newer.getId());
    }

    @Test
    void findPublishTargetsForUpdate_returnsPending_whenReservationHasPassed() {
        // Given
        SolutionEvaluationTask expired = pending(1L, NOW.minusSeconds(30), NOW.minusSeconds(1));
        em.clear();

        // When
        List<SolutionEvaluationTask> targets = repository.findPublishTargetsForUpdate(NOW, 10);

        // Then
        assertThat(targets).extracting(SolutionEvaluationTask::getId).containsExactly(expired.getId());
    }

    @Test
    void findPublishTargetsForUpdate_skipsPending_whenStillReserved() {
        // Given
        pending(1L, NOW.minusSeconds(30), NOW.plusSeconds(5));
        em.clear();

        // When & Then
        assertThat(repository.findPublishTargetsForUpdate(NOW, 10)).isEmpty();
    }

    @Test
    void findPublishTargetsForUpdate_returnsRetryWait_onlyWhenRetryTimeHasPassed() {
        // Given
        SolutionEvaluationTask due = retryWait(1L, NOW.minusSeconds(1));
        retryWait(2L, NOW.plusSeconds(10));
        em.clear();

        // When
        List<SolutionEvaluationTask> targets = repository.findPublishTargetsForUpdate(NOW, 10);

        // Then
        assertThat(targets).extracting(SolutionEvaluationTask::getId).containsExactly(due.getId());
    }

    @Test
    void findPublishTargetsForUpdate_skipsProcessingAndFinishedTasks() {
        // Given
        SolutionEvaluationTask processing = pending(1L, NOW.minusSeconds(30), null);
        processing.markPublished(NOW);
        repository.saveAndFlush(processing);
        SolutionEvaluationTask succeeded = pending(2L, NOW.minusSeconds(30), null);
        succeeded.markSucceeded(501L, NOW);
        repository.saveAndFlush(succeeded);
        SolutionEvaluationTask failed = pending(3L, NOW.minusSeconds(30), null);
        failed.markFailed("AI_SERVER_UNAVAILABLE", "publish_failed", NOW);
        repository.saveAndFlush(failed);
        em.clear();

        // When & Then
        assertThat(repository.findPublishTargetsForUpdate(NOW, 10)).isEmpty();
    }

    @Test
    void findPublishTargetsForUpdate_respectsLimit() {
        // Given
        SolutionEvaluationTask first = pending(1L, NOW.minusSeconds(5), null);
        pending(2L, NOW.minusSeconds(4), null);
        pending(3L, NOW.minusSeconds(3), null);
        em.clear();

        // When
        List<SolutionEvaluationTask> targets = repository.findPublishTargetsForUpdate(NOW, 1);

        // Then
        assertThat(targets).extracting(SolutionEvaluationTask::getId).containsExactly(first.getId());
    }

    @Test
    void findExpiredPendingIds_returnsOnlyPendingCreatedBeforeThreshold() {
        // Given
        SolutionEvaluationTask old = pending(1L, NOW.minusMinutes(3), null);
        pending(2L, NOW.minusSeconds(30), null);
        SolutionEvaluationTask oldButProcessing = pending(3L, NOW.minusMinutes(3), null);
        oldButProcessing.markPublished(NOW);
        repository.saveAndFlush(oldButProcessing);
        em.clear();

        // When
        List<Long> ids = repository.findExpiredPendingIds(NOW.minusMinutes(2));

        // Then
        assertThat(ids).containsExactly(old.getId());
    }

    // 재시도 대기 상태의 작업을 저장 (옛 방식에서 남은 작업)
    private SolutionEvaluationTask retryWait(Long userId, LocalDateTime nextRetryAt) {
        SolutionEvaluationTask task = repository.saveAndFlush(
                new SolutionEvaluationTask(userId, 10L, Category.ARRAY, "풀이", NOW.minusMinutes(1)));
        task.startProcessing(NOW.minusMinutes(1));
        task.scheduleRetry(nextRetryAt, "AI_SERVER_UNAVAILABLE", "ai_server_unavailable", NOW.minusMinutes(1));
        return repository.saveAndFlush(task);
    }
}
