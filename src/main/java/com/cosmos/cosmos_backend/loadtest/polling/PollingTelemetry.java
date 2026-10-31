package com.cosmos.cosmos_backend.loadtest.polling;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local-only, bounded completion journal. No writes to application tables. */
@Component
@Profile("local")
public class PollingTelemetry {
    public record Completion(long taskId, String status, long committedAtMs) {}
    private final Map<Long, Completion> completions = new ConcurrentHashMap<>();
    private final MeterRegistry registry;
    private volatile String run = "idle";
    public PollingTelemetry(MeterRegistry registry) {
        this.registry = registry;
        io.micrometer.core.instrument.Gauge.builder("cosmos.polling.process.rss.bytes", () -> {
            try (var lines = java.nio.file.Files.lines(java.nio.file.Path.of("/proc/self/status"))) {
                return lines.filter(line -> line.startsWith("VmRSS:"))
                        .mapToDouble(line -> Double.parseDouble(line.trim().split("\\s+")[1]) * 1024)
                        .findFirst().orElse(Double.NaN);
            } catch (java.io.IOException ignored) { return Double.NaN; }
        }).register(registry);
    }
    public String run() { return run; }
    public Map<Long, Completion> completions() { return Map.copyOf(completions); }
    public void begin(String run) {
        if (!run.matches("poll-[a-z0-9-]{1,80}")) throw new IllegalArgumentException("invalid run");
        this.run = run;
    }
    public void end() { this.run = "idle"; }
    public void afterCommit(long taskId, String status) {
        String completionRun = run;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                long timestamp = Instant.now().toEpochMilli();
                if (completions.size() >= 20000) {
                    completions.keySet().stream().min(Long::compare).ifPresent(completions::remove);
                }
                completions.put(taskId, new Completion(taskId, status, timestamp));
                registry.counter("cosmos.polling.committed", "run", completionRun, "status", status).increment();
            }
        });
    }
}
