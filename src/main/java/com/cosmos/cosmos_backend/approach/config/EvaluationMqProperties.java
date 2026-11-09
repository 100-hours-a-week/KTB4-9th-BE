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
        @DefaultValue("5m") Duration requestTtl
) {
}
