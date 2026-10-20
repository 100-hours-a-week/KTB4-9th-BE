package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 평가 워커를 주기적으로 실행. 전용 스레드 1개를 직접 만들어 씀.
 * (TaskScheduler 빈을 등록하면 Spring Boot 기본 스케줄러가 사라져 battle 스케줄이 영향을 받기 때문)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionEvaluationScheduler {

    private final SolutionEvaluationWorker worker;
    private final EvaluationWorkerProperties properties;

    private ScheduledExecutorService executor;

    // 서버가 뜰 때 워커 전용 스레드를 만들어 주기 실행을 시작 (비활성이면 아무것도 안 함)
    @PostConstruct
    void start() {
        if (!properties.enabled()) {
            log.info("자연어 풀이 평가 워커 비활성화 상태");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "evaluation-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runSafely, 0, properties.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("자연어 풀이 평가 워커 시작: pollInterval={}", properties.pollInterval());
    }

    // 서버가 내려갈 때 새 실행을 막음 (진행 중이던 작업은 끊지 않음)
    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // 예외가 밖으로 나가면 이후 주기 실행이 멈추므로 여기서 모두 잡음
    private void runSafely() {
        try {
            worker.runUntilEmpty();
        } catch (Exception e) {
            log.error("자연어 풀이 평가 워커 실행 중 오류", e);
        }
    }
}
