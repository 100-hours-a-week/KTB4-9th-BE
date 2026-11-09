package com.cosmos.cosmos_backend.approach.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EvaluationSwitchGuardTest {

    private EvaluationWorkerProperties worker(boolean enabled) {
        return new EvaluationWorkerProperties(
                enabled, Duration.ofSeconds(5), 2, 3, Duration.ofSeconds(15), Duration.ofMinutes(2), Duration.ofSeconds(30));
    }

    private EvaluationMqProperties mq(boolean enabled) {
        return new EvaluationMqProperties(
                enabled, "evaluation.request", "evaluation.result", "evaluation.request.dlq", Duration.ofMinutes(5));
    }

    @Test
    void create_throws_whenWorkerAndMqAreBothEnabled() {
        // When & Then
        assertThatThrownBy(() -> new EvaluationSwitchGuard(worker(true), mq(true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void create_succeeds_whenOnlyWorkerIsEnabled() {
        // When & Then
        assertThat(new EvaluationSwitchGuard(worker(true), mq(false))).isNotNull();
    }

    @Test
    void create_succeeds_whenOnlyMqIsEnabled() {
        // When & Then
        assertThat(new EvaluationSwitchGuard(worker(false), mq(true))).isNotNull();
    }

    @Test
    void create_succeeds_whenBothAreDisabled() {
        // When & Then
        assertThat(new EvaluationSwitchGuard(worker(false), mq(false))).isNotNull();
    }
}
