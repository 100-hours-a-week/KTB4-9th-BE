package com.cosmos.cosmos_backend.loadtest.polling;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class PollingTelemetryTest {
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }
    @Test void recordsOnlyAfterCommitAndUsesCommitTime() {
        var registry = new SimpleMeterRegistry();
        var probe = new PollingTelemetry(registry);
        probe.begin("poll-wait-1-test");
        TransactionSynchronizationManager.initSynchronization();
        probe.afterCommit(42L, "SUCCEEDED");
        assertTrue(probe.completions().isEmpty());
        long beforeCommit = System.currentTimeMillis();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        var completion = probe.completions().get(42L);
        assertEquals("SUCCEEDED", completion.status());
        assertTrue(completion.committedAtMs() >= beforeCommit);
        assertEquals(1, registry.get("cosmos.polling.committed").tag("run", "poll-wait-1-test").counter().count());
    }
    @Test void rollbackDoesNotProduceCompletion() {
        var probe = new PollingTelemetry(new SimpleMeterRegistry());
        TransactionSynchronizationManager.initSynchronization();
        probe.afterCommit(7L, "FAILED");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertTrue(probe.completions().isEmpty());
    }
}
