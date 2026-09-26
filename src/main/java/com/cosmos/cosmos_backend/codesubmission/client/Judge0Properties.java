package com.cosmos.cosmos_backend.codesubmission.client;

import com.cosmos.cosmos_backend.common.Language;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Judge0 서버 접속 설정 (application.yaml의 judge0.*). */
@ConfigurationProperties(prefix = "judge0")
public record Judge0Properties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration pollInterval,
        Duration maxWait,
        int batchSize,
        int maxMemoryKb,
        Map<Language, Integer> languageIds
) {
}
