package com.cosmos.cosmos_backend.approach.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 자연어 풀이 평가 워커 설정 (application.yaml의 evaluation.worker.*). */
@ConfigurationProperties(prefix = "evaluation.worker")
public record EvaluationWorkerProperties(
        // 워커를 켤지 여부 (기본은 꺼짐)
        @DefaultValue("false") boolean enabled,
        // 처리할 작업이 없을 때 다시 확인하기까지의 간격
        @DefaultValue("3s") Duration pollInterval,
        // 워커 개수 (AI 서버에 동시에 보내는 평가 요청 수)
        @DefaultValue("5") int concurrency,
        // 한 작업을 시도하는 최대 횟수 (최초 1회 + 재시도)
        @DefaultValue("3") int maxAttempts,
        // 재시도 전에 기다리는 시간
        @DefaultValue("15s") Duration retryDelay,
        // PROCESSING이 이 시간을 넘으면 멈춘 것으로 보고 복구
        @DefaultValue("2m") Duration stuckAfter,
        // 멈춘 작업을 확인하는 주기
        @DefaultValue("30s") Duration recoveryInterval
) {
}
