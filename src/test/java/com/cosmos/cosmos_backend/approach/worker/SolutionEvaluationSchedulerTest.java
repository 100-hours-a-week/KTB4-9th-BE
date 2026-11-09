package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationSchedulerTest {

    @Mock
    private SolutionEvaluationWorker worker;

    @Mock
    private SolutionEvaluationRecoverer recoverer;

    private SolutionEvaluationScheduler scheduler;

    private SolutionEvaluationScheduler schedulerWith(boolean enabled, int concurrency) {
        EvaluationWorkerProperties properties = new EvaluationWorkerProperties(
                enabled, Duration.ofMillis(20), concurrency, 3, Duration.ofSeconds(15), Duration.ofMinutes(2), Duration.ofMillis(20));
        scheduler = new SolutionEvaluationScheduler(worker, recoverer, properties);
        return scheduler;
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void start_doesNotRunAnything_whenDisabled() {
        // When
        schedulerWith(false, 5).start();

        // Then
        verify(worker, after(200).never()).runUntilEmpty();
        verify(recoverer, never()).recover();
    }

    @Test
    void start_runsWorkerAndRecoveryRepeatedly_whenEnabled() {
        // When
        schedulerWith(true, 1).start();

        // Then
        verify(worker, timeout(2000).atLeast(2)).runUntilEmpty();
        verify(recoverer, timeout(2000).atLeast(2)).recover();
    }

    @Test
    void start_runsWorkersOnMultipleThreads_whenConcurrencyIsGreaterThanOne() throws InterruptedException {
        // Given: 워커가 동시에 3개 실행되어야만 풀리는 래치
        CountDownLatch allRunning = new CountDownLatch(3);
        ConcurrentHashMap<String, Boolean> threadNames = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            threadNames.put(Thread.currentThread().getName(), true);
            allRunning.countDown();
            allRunning.await(2, TimeUnit.SECONDS);
            return null;
        }).when(worker).runUntilEmpty();

        // When
        schedulerWith(true, 3).start();

        // Then
        assertThat(allRunning.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadNames).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void start_keepsRunningWorker_evenIfWorkerThrows() {
        // Given
        doThrow(new IllegalStateException("오류")).doNothing().when(worker).runUntilEmpty();

        // When
        schedulerWith(true, 1).start();

        // Then
        verify(worker, timeout(2000).atLeast(2)).runUntilEmpty();
    }

    @Test
    void start_keepsRunningRecovery_evenIfRecoveryThrows() {
        // Given
        doThrow(new IllegalStateException("오류")).doNothing().when(recoverer).recover();

        // When
        schedulerWith(true, 1).start();

        // Then
        verify(recoverer, timeout(2000).atLeast(2)).recover();
    }

    @Test
    void stop_stopsFurtherRuns() throws InterruptedException {
        // Given
        schedulerWith(true, 1).start();
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
