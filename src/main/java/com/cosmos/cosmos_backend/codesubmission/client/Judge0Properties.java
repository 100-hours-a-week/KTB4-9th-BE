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

    /** 배치 수만큼 채점할 때 걸릴 수 있는 최대 시간 (배치마다 제출 1번 + 대기 + 마지막 조회 1번). */
    public Duration worstCaseJudgingTime(int batches) {
        Duration call = connectTimeout.plus(readTimeout);
        return call.multipliedBy(2).plus(maxWait).multipliedBy(batches);
    }
}
