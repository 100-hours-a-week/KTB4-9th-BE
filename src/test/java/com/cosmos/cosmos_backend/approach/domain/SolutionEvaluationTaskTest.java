package com.cosmos.cosmos_backend.approach.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.common.Category;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SolutionEvaluationTaskTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 19, 3, 30, 0);
    private static final LocalDateTime T1 = T0.plusSeconds(1);
    private static final LocalDateTime T2 = T0.plusSeconds(6);

    private SolutionEvaluationTask newTask() {
        return new SolutionEvaluationTask(1L, 10L, Category.ARRAY, "풀이", T0);
    }

    private SolutionEvaluationTask processingTask() {
        SolutionEvaluationTask task = newTask();
        task.startProcessing(T1);
        return task;
    }

    @Test
    void newTask_startsPendingAndActive() {
        // When
        SolutionEvaluationTask task = newTask();

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PENDING);
        assertThat(task.getWorkerAttemptCount()).isZero();
        assertThat(task.getActiveSlot()).isEqualTo(SolutionEvaluationTask.ACTIVE_SLOT);
        assertThat(task.getCreatedAt()).isEqualTo(T0);
        assertThat(task.getStartedAt()).isNull();
        assertThat(task.getSubmissionId()).isNull();
    }

    @Test
    void startProcessing_movesPendingToProcessing_andCountsAttempt() {
        // Given
        SolutionEvaluationTask task = newTask();

        // When
        task.startProcessing(T1);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        assertThat(task.getWorkerAttemptCount()).isEqualTo(1);
        assertThat(task.getStartedAt()).isEqualTo(T1);
    }

    @Test
    void startProcessing_fromRetryWait_countsSecondAttempt_andClearsNextRetryAt() {
        // Given
        SolutionEvaluationTask task = processingTask();
        task.scheduleRetry(T2, "AI_SERVER_TIMEOUT", "시간 초과", T1);

        // When
        task.startProcessing(T2);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        assertThat(task.getWorkerAttemptCount()).isEqualTo(2);
        assertThat(task.getNextRetryAt()).isNull();
    }

    @Test
    void startProcessing_throws_whenAlreadyProcessing() {
        // Given
        SolutionEvaluationTask task = processingTask();

        // When & Then
        assertThatThrownBy(() -> task.startProcessing(T2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scheduleRetry_movesToRetryWait_andKeepsActive() {
        // Given
        SolutionEvaluationTask task = processingTask();

        // When
        task.scheduleRetry(T2, "AI_SERVER_UNAVAILABLE", "연결 실패", T1);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.RETRY_WAIT);
        assertThat(task.getNextRetryAt()).isEqualTo(T2);
        assertThat(task.getErrorCode()).isEqualTo("AI_SERVER_UNAVAILABLE");
        assertThat(task.getActiveSlot()).isEqualTo(SolutionEvaluationTask.ACTIVE_SLOT);
    }

    @Test
    void markSucceeded_releasesActiveSlot_savesSubmissionId_andClearsError() {
        // Given
        SolutionEvaluationTask task = processingTask();
        task.scheduleRetry(T2, "AI_SERVER_TIMEOUT", "시간 초과", T1);
        task.startProcessing(T2);

        // When
        task.markSucceeded(501L, T2);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.SUCCEEDED);
        assertThat(task.getSubmissionId()).isEqualTo(501L);
        assertThat(task.getActiveSlot()).isNull();
        assertThat(task.getErrorCode()).isNull();
        assertThat(task.getErrorMessage()).isNull();
        assertThat(task.getCompletedAt()).isEqualTo(T2);
    }

    @Test
    void markFailed_releasesActiveSlot_andRecordsError() {
        // Given
        SolutionEvaluationTask task = processingTask();

        // When
        task.markFailed("AI_SERVER_UNAVAILABLE", "최종 실패", T2);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.FAILED);
        assertThat(task.getActiveSlot()).isNull();
        assertThat(task.getErrorCode()).isEqualTo("AI_SERVER_UNAVAILABLE");
        assertThat(task.getCompletedAt()).isEqualTo(T2);
    }

    @Test
    void transitions_throw_whenTaskIsStillPending() {
        // Given
        SolutionEvaluationTask task = newTask();

        // When & Then
        assertThatThrownBy(() -> task.scheduleRetry(T2, "code", "msg", T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task.markSucceeded(1L, T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task.markFailed("code", "msg", T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalStates_rejectAnyFurtherTransition() {
        // Given
        SolutionEvaluationTask succeeded = processingTask();
        succeeded.markSucceeded(1L, T2);
        SolutionEvaluationTask failed = processingTask();
        failed.markFailed("code", "msg", T2);

        // When & Then
        for (SolutionEvaluationTask task : new SolutionEvaluationTask[]{succeeded, failed}) {
            assertThatThrownBy(() -> task.startProcessing(T2)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> task.scheduleRetry(T2, "code", "msg", T2)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> task.markSucceeded(1L, T2)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> task.markFailed("code", "msg", T2)).isInstanceOf(IllegalStateException.class);
        }
    }
}
