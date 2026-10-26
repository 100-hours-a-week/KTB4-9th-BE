package com.cosmos.cosmos_backend.codesubmission.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Judge0PropertiesTest {

    @Test
    void worstCaseJudgingTime_countsSubmitPollAndLastRequestPerBatch() {
        // Given
        Judge0Properties props = new Judge0Properties("http://judge0", Duration.ofSeconds(3), Duration.ofSeconds(10),
                Duration.ofSeconds(1), Duration.ofSeconds(30), 20, 512000, Map.of());

        // When & Then: 배치 하나당 (연결 3 + 읽기 10) x 2 + 대기 30 = 56초
        assertThat(props.worstCaseJudgingTime(1)).isEqualTo(Duration.ofSeconds(56));
        assertThat(props.worstCaseJudgingTime(3)).isEqualTo(Duration.ofSeconds(168));
    }
}
