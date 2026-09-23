package com.cosmos.cosmos_backend.approach.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 서버 접속 설정 (application.yaml의 ai.server.*). */
@ConfigurationProperties(prefix = "ai.server")
public record AiClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
