package com.cosmos.cosmos_backend.dailyBattle.metrics;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// 부하테스트를 위한 메트릭
@Component
public class BattleMetrics {

    private static final ParticipationStatus[] QUEUE_STATUSES = {
            ParticipationStatus.SUBMITTED,
            ParticipationStatus.GRADING,
            ParticipationStatus.GRADING_COMPLETED,
            ParticipationStatus.GRADING_FAILED
    };

    private final MeterRegistry registry;
    private final Timer queueWait;
    private final Timer endToEnd;
    private final Timer resultPublishDelay;
    private final Map<ParticipationStatus, AtomicLong> queueDepths =
            new EnumMap<>(ParticipationStatus.class);
    private final AtomicLong maxQueueDepth = new AtomicLong();
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicLong resultPublishedTimestamp = new AtomicLong();
    private final AtomicLong pendingAtFirstFinalize = new AtomicLong();
    private final AtomicBoolean firstFinalizeRecorded = new AtomicBoolean();
    private final Counter finalizeAttempts;

    public BattleMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queueWait = timer("battle.grading.queue.wait", "제출 후 워커가 선점할 때까지 걸린 시간");
        this.endToEnd = timer("battle.grading.end.to.end", "제출부터 채점 완료까지 걸린 시간");
        this.resultPublishDelay = timer("battle.result.publish.delay", "예정 시각 대비 결과 공개 지연");
        this.finalizeAttempts = Counter.builder("battle.result.finalize.attempts")
                .description("결과 공개 확정 시도 횟수")
                .register(registry);

        for (ParticipationStatus status : QUEUE_STATUSES) {
            AtomicLong value = new AtomicLong();
            queueDepths.put(status, value);
            Gauge.builder("battle.queue.depth", value, AtomicLong::get)
                    .tag("status", status.name())
                    .description("상태별 데일리 배틀 작업 수")
                    .register(registry);
        }

        Gauge.builder("battle.queue.max.depth", maxQueueDepth, AtomicLong::get)
                .description("관측 기간 중 SUBMITTED 최대 적재량")
                .register(registry);
        Gauge.builder("battle.worker.active", activeWorkers, AtomicInteger::get)
                .description("현재 실행 중인 배틀 채점 워커 수")
                .register(registry);
        Gauge.builder("battle.result.published.timestamp", resultPublishedTimestamp, AtomicLong::get)
                .description("결과가 공개된 Unix timestamp(초)")
                .register(registry);
        Gauge.builder("battle.result.pending.at.first.finalize", pendingAtFirstFinalize, AtomicLong::get)
                .description("결과 공개 예정 시각 첫 확인에서 남아 있던 SUBMITTED+GRADING 작업 수")
                .register(registry);
    }

    public void recordClaim(Instant submittedAt, Instant claimedAt) {
        if (submittedAt != null) {
            queueWait.record(nonNegative(Duration.between(submittedAt, claimedAt)));
        }
        taskCounter("claimed").increment();
    }

    public void recordProcessing(Duration duration, String outcome) {
        Timer.builder("battle.grading.processing")
                .tag("outcome", outcome)
                .description("워커가 작업을 선점한 뒤 채점을 마칠 때까지 걸린 시간")
                .publishPercentileHistogram()
                .register(registry)
                .record(nonNegative(duration));
    }

    public void recordCompleted(Instant submittedAt, Instant completedAt) {
        if (submittedAt != null) {
            endToEnd.record(nonNegative(Duration.between(submittedAt, completedAt)));
        }
        taskCounter("completed").increment();
    }

    public void recordRetry() {
        taskCounter("retry").increment();
    }

    public void recordFailure() {
        taskCounter("failed").increment();
    }

    public void updateQueueDepths(Map<ParticipationStatus, Long> counts) {
        for (ParticipationStatus status : QUEUE_STATUSES) {
            long count = counts.getOrDefault(status, 0L);
            queueDepths.get(status).set(count);
            if (status == ParticipationStatus.SUBMITTED) {
                maxQueueDepth.accumulateAndGet(count, Math::max);
            }
        }
    }

    public void setActiveWorkers(int count) {
        activeWorkers.set(Math.max(count, 0));
    }

    public void resetBattleRun() {
        maxQueueDepth.set(0);
        resultPublishedTimestamp.set(0);
        pendingAtFirstFinalize.set(0);
        firstFinalizeRecorded.set(false);
    }

    public void recordFinalizeAttempt() {
        finalizeAttempts.increment();
        if (firstFinalizeRecorded.compareAndSet(false, true)) {
            long submitted = queueDepths.get(ParticipationStatus.SUBMITTED).get();
            long grading = queueDepths.get(ParticipationStatus.GRADING).get();
            pendingAtFirstFinalize.set(submitted + grading);
        }
    }

    public void recordResultPublished(Instant scheduledAt, Instant publishedAt) {
        resultPublishedTimestamp.set(publishedAt.getEpochSecond());
        resultPublishDelay.record(nonNegative(Duration.between(scheduledAt, publishedAt)));
    }

    private Counter taskCounter(String outcome) {
        return Counter.builder("battle.grading.tasks")
                .tag("outcome", outcome)
                .description("배틀 채점 작업 결과별 누적 횟수")
                .register(registry);
    }

    private Timer timer(String name, String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentileHistogram()
                .register(registry);
    }

    private Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
