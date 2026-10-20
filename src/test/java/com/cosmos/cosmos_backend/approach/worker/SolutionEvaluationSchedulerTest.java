package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationSchedulerTest {

    @Mock
    private SolutionEvaluationWorker worker;

    private SolutionEvaluationScheduler scheduler;

    private SolutionEvaluationScheduler schedulerWith(boolean enabled) {
        scheduler = new SolutionEvaluationScheduler(worker, new EvaluationWorkerProperties(enabled, Duration.ofMillis(20)));
        return scheduler;
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void start_doesNotRunWorker_whenDisabled() {
        // When
        schedulerWith(false).start();

        // Then
        verify(worker, after(200).never()).runUntilEmpty();
    }

    @Test
    void start_runsWorkerRepeatedly_whenEnabled() {
        // When
        schedulerWith(true).start();

        // Then
        verify(worker, timeout(2000).atLeast(2)).runUntilEmpty();
    }

    @Test
    void start_keepsRunning_evenIfWorkerThrows() {
        // Given
        doThrow(new IllegalStateException("오류")).doNothing().when(worker).runUntilEmpty();

        // When
        schedulerWith(true).start();

        // Then
        verify(worker, timeout(2000).atLeast(2)).runUntilEmpty();
    }

    @Test
    void stop_stopsFurtherRuns() throws InterruptedException {
        // Given
        schedulerWith(true).start();
        verify(worker, timeout(2000).atLeastOnce()).runUntilEmpty();

        // When
        scheduler.stop();
        Thread.sleep(100);
        int callsAfterStop = mockingDetails(worker).getInvocations().size();
        Thread.sleep(200);

        // Then
        assertThat(mockingDetails(worker).getInvocations()).hasSize(callsAfterStop);
    }
}
