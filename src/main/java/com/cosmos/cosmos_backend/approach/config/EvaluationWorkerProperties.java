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
        @DefaultValue("3s") Duration pollInterval
) {
}
