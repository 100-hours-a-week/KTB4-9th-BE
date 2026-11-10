package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationRelayService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 발행 대기 작업을 요청 큐로 보냄. MQ를 켰을 때만 등록됨.
 * 전용 스레드를 직접 만들어 씀 (TaskScheduler 빈을 등록하면 Spring Boot 기본 스케줄러가 사라지기 때문).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "evaluation.mq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EvaluationRelay {

    private final SolutionEvaluationRelayService relayService;
    private final SolutionEvaluationExecutionService executionService;
    private final EvaluationRequestPublisher publisher;
    private final EvaluationMqProperties properties;

    private ScheduledExecutorService executor;

    // 서버가 뜰 때 릴레이를 시작
    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "evaluation-relay");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runSafely, 0, properties.relayInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("평가 릴레이 시작: interval={}, batchSize={}", properties.relayInterval(), properties.relayBatchSize());
    }

    // 서버가 내려갈 때 새 실행을 막음
    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // 예외가 밖으로 나가면 이후 주기 실행이 멈추므로 여기서 모두 잡음
    private void runSafely() {
        try {
            runOnce();
        } catch (Exception e) {
            log.error("평가 릴레이 실행 중 오류", e);
        }
    }

    /** 한 주기: 오래된 PENDING 정리 -> 발행 대상 선점 -> 하나씩 발행. */
    void runOnce() {
        // 1. 제한 시간이 지난 PENDING을 실패 처리 (선점보다 먼저)
        for (Long id : relayService.findExpiredPendingIds()) {
            failExpired(id);
        }

        // 2. 발행 대상을 선점하고 예약 시각을 적음
        List<Long> taskIds = relayService.claimForPublish(properties.relayBatchSize());

        // 3. 하나씩 발행. 브로커 문제로 실패하면 이번 주기의 나머지는 보내지 않음
        for (Long id : taskIds) {
            if (!publishOne(id)) {
                break;
            }
        }
    }

    // 작업 1건을 발행. 브로커 문제면 false (나머지 중단), 이 작업만의 문제면 true (다음 작업으로)
    private boolean publishOne(Long taskId) {
        // 1. 문제 정보를 조회해 AI 요청을 만듦 (실패해도 그 작업만 건너뛰고, 2분 규칙이 정리함)
        AiEvaluationRequest request;
        try {
            request = executionService.prepare(taskId).request();
        } catch (Exception e) {
            log.error("평가 요청 준비 실패: taskId={}", taskId, e);
            return true;
        }

        // 2. 발행하고 확인을 기다림 (트랜잭션 밖)
        if (!publisher.publish(taskId, request)) {
            return false;
        }

        // 3. 확인을 받았으면 PROCESSING으로 바꿈 (이미 끝난 작업이면 바뀌지 않음)
        relayService.markPublished(taskId);
        return true;
    }

    // 오래된 PENDING 1건을 실패 처리 (실패해도 다음 작업을 계속 처리)
    private void failExpired(Long taskId) {
        try {
            relayService.failExpiredPending(taskId);
        } catch (Exception e) {
            log.error("오래된 PENDING 실패 처리 중 오류: taskId={}", taskId, e);
        }
    }
}
