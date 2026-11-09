package com.cosmos.cosmos_backend.dailyBattle.metrics;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BattleMetricsTest {

    private SimpleMeterRegistry registry;
    private BattleMetrics battleMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        battleMetrics = new BattleMetrics(registry);
    }

    @Test
    void recordsQueueProcessingAndEndToEndDurations() {
        Instant submittedAt = Instant.parse("2026-10-24T00:00:00Z");
        Instant claimedAt = submittedAt.plusMillis(120);
        Instant completedAt = submittedAt.plusMillis(320);

        battleMetrics.recordClaim(submittedAt, claimedAt);
        battleMetrics.recordProcessing(Duration.ofMillis(200), "success");
        battleMetrics.recordCompleted(submittedAt, completedAt);

        assertThat(registry.get("battle.grading.queue.wait").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(120);
        assertThat(registry.get("battle.grading.processing").tag("outcome", "success").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(200);
        assertThat(registry.get("battle.grading.end.to.end").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(320);
        assertThat(registry.get("battle.grading.tasks").tag("outcome", "claimed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("battle.grading.tasks").tag("outcome", "completed").counter().count())
                .isEqualTo(1);
    }

    @Test
    void capturesQueuePeakAndPendingCountAtFirstFinalizeOnly() {
        battleMetrics.updateQueueDepths(Map.of(
                ParticipationStatus.SUBMITTED, 7L,
                ParticipationStatus.GRADING, 2L
        ));
        battleMetrics.recordFinalizeAttempt();

        battleMetrics.updateQueueDepths(Map.of(
                ParticipationStatus.SUBMITTED, 1L,
                ParticipationStatus.GRADING, 1L
        ));
        battleMetrics.recordFinalizeAttempt();

        assertGauge("battle.queue.max.depth", 7);
        assertGauge("battle.result.pending.at.first.finalize", 9);
        assertThat(registry.get("battle.result.finalize.attempts").counter().count()).isEqualTo(2);
    }

    @Test
    void resetsRunSpecificGaugesBeforeAnotherBattleRun() {
        battleMetrics.updateQueueDepths(Map.of(ParticipationStatus.SUBMITTED, 5L));
        battleMetrics.recordFinalizeAttempt();
        battleMetrics.recordResultPublished(
                Instant.parse("2026-10-24T00:00:00Z"),
                Instant.parse("2026-10-24T00:00:01Z")
        );

        battleMetrics.resetBattleRun();
        battleMetrics.updateQueueDepths(Map.of(
                ParticipationStatus.SUBMITTED, 2L,
                ParticipationStatus.GRADING, 1L
        ));
        battleMetrics.recordFinalizeAttempt();

        assertGauge("battle.queue.max.depth", 2);
        assertGauge("battle.result.pending.at.first.finalize", 3);
        assertGauge("battle.result.published.timestamp", 0);
    }

    private void assertGauge(String name, double expected) {
        assertThat(registry.get(name).gauge().value()).isEqualTo(expected);
    }
}
