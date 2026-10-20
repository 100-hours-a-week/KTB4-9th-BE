package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 평가 워커와 멈춘 작업 복구를 주기적으로 실행. 전용 스레드 풀을 직접 만들어 씀.
 * (TaskScheduler 빈을 등록하면 Spring Boot 기본 스케줄러가 사라져 battle 스케줄이 영향을 받기 때문)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionEvaluationScheduler {

    private final SolutionEvaluationWorker worker;
    private final SolutionEvaluationRecoverer recoverer;
    private final EvaluationWorkerProperties properties;

    private ScheduledExecutorService executor;

    // 서버가 뜰 때 워커와 복구 실행을 시작 (비활성이면 아무것도 안 함)
    @PostConstruct
    void start() {
        if (!properties.enabled()) {
            log.info("자연어 풀이 평가 워커 비활성화 상태");
            return;
        }

        // 1. 워커 N개 + 복구 1개를 돌릴 스레드 풀 생성
        int workerCount = properties.concurrency();
        AtomicInteger threadNumber = new AtomicInteger(1);
        executor = Executors.newScheduledThreadPool(workerCount + 1, runnable -> {
            Thread thread = new Thread(runnable, "evaluation-worker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });

        // 2. 워커를 N개 등록 (모두 같은 순간에 DB를 조회하지 않도록 시작 시점을 조금씩 어긋나게 함)
        long intervalMillis = properties.pollInterval().toMillis();
        for (int i = 0; i < workerCount; i++) {
            executor.scheduleWithFixedDelay(this::runWorker, intervalMillis * i / workerCount, intervalMillis, TimeUnit.MILLISECONDS);
        }

        // 3. 멈춘 작업 복구는 별도 스레드에서 주기 실행 (워커가 바빠도 밀리지 않게)
        executor.scheduleWithFixedDelay(this::runRecovery, 0, properties.recoveryInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("자연어 풀이 평가 워커 시작: concurrency={}, pollInterval={}", workerCount, properties.pollInterval());
    }

    // 서버가 내려갈 때 새 실행을 막음 (진행 중이던 작업은 끊지 않음)
    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // 예외가 밖으로 나가면 이후 주기 실행이 멈추므로 여기서 모두 잡음
    private void runWorker() {
        try {
            worker.runUntilEmpty();
        } catch (Exception e) {
            log.error("자연어 풀이 평가 워커 실행 중 오류", e);
        }
    }

    private void runRecovery() {
        try {
            recoverer.recover();
        } catch (Exception e) {
            log.error("멈춘 평가 작업 복구 중 오류", e);
        }
    }
}
