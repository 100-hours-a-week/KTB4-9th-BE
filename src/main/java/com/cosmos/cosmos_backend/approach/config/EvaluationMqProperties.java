package com.cosmos.cosmos_backend.approach.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 자연어 풀이 평가 MQ 설정 (application.yaml의 evaluation.mq.*). */
@ConfigurationProperties(prefix = "evaluation.mq")
public record EvaluationMqProperties(
        // MQ 방식을 켤지 여부 (켜면 기존 워커는 꺼야 함)
        @DefaultValue("false") boolean enabled,
        // 백엔드가 AI 서버로 보내는 요청 큐
        @DefaultValue("evaluation.request") String requestQueue,
        // AI 서버가 결과를 보내는 큐
        @DefaultValue("evaluation.result") String resultQueue,
        // 만료되거나 거절된 요청이 가는 큐
        @DefaultValue("evaluation.request.dlq") String requestDlq,
        // 요청이 이 시간 안에 처리되지 않으면 만료되어 DLQ로 이동
        @DefaultValue("5m") Duration requestTtl,
        // 선점한 작업을 이 시간 동안 다른 릴레이가 가져가지 않음 (발행 실패 시 다시 시도하기까지의 간격)
        @DefaultValue("10s") Duration publishLease,
        // 접수 후 이 시간이 지나도 PENDING이면 FAILED로 처리
        @DefaultValue("2m") Duration pendingFailAfter,
        // 릴레이가 발행 대기 작업을 확인하는 주기
        @DefaultValue("1s") Duration relayInterval,
        // 릴레이가 한 번에 선점해서 발행하는 최대 작업 수
        @DefaultValue("50") int relayBatchSize,
        // 발행 확인(Confirm)을 기다리는 최대 시간
        @DefaultValue("5s") Duration confirmTimeout
) {
}
